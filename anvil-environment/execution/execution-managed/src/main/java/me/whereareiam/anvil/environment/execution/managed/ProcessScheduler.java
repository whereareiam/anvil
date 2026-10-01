package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Bounds independent preparation and startup work, stops queued work after failure, and settles every active owner.
 */
public final class ProcessScheduler {
	private final int parallelism;
	private final int memoryBudget;

	/**
	 * Creates limits for one preparation or bulk-start operation.
	 *
	 * @param parallelism maximum independent operations running concurrently
	 * @param memoryBudget declared heap allowance for concurrent starts, in MiB
	 * @throws IllegalArgumentException when either limit is nonpositive
	 */
	public ProcessScheduler(int parallelism, int memoryBudget) {
		if (parallelism < 1 || memoryBudget < 1) throw new IllegalArgumentException("Process scheduling limits must be positive");
		this.parallelism = parallelism;
		this.memoryBudget = memoryBudget;
	}

	/**
	 * Runs independent operations with bounded concurrency and settles in-flight work on failure.
	 *
	 * @param inputs work items for this operation
	 * @param action operation applied to each item
	 * @param <T> work item type
	 */
	public <T> void run(@NotNull Collection<T> inputs, @NotNull Consumer<T> action) {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		try (var executor = Executors.newFixedThreadPool(parallelism)) {
			var tasks = inputs.stream().map(input -> executor.submit(() -> {
				if (failure.get() != null) return;
				try {
					action.accept(input);
				} catch (RuntimeException | Error caught) {
					if (!failure.compareAndSet(null, caught)) {
						Throwable first = failure.get();
						if (caught != first) first.addSuppressed(caught);
					}
				}
			})).toList();

			try {
				for (var task : tasks) task.get();
			} catch (InterruptedException interrupted) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();

				throw new ProvisioningException("Interrupted during scenario preparation", interrupted);
			} catch (ExecutionException unexpected) {
				throw new ProvisioningException("Scenario preparation failed", unexpected.getCause());
			}
		}

		Throwable first = failure.get();
		if (first instanceof Error error) throw error;
		if (first != null) throw (RuntimeException) first;
	}

	/**
	 * Starts processes within the concurrency and declared heap allowances.
	 * A process larger than the heap allowance starts alone; its permits are released on readiness
	 * or failure, so this does not constrain the total memory of running processes.
	 *
	 * @param inputs processes in one startup dependency layer
	 * @param action starts a process and waits for readiness
	 */
	public void start(@NotNull Collection<ProcessSpec> inputs, @NotNull Consumer<ProcessSpec> action) {
		Semaphore memory = new Semaphore(memoryBudget, true);
		run(inputs, process -> {
			int permits = Math.min(memoryBudget, process.getMemoryMegabytes());
			try {
				memory.acquire(permits);
				try {
					action.accept(process);
				} finally {
					memory.release(permits);
				}
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				throw new ProvisioningException("Interrupted waiting for startup capacity", failure);
			}
		});
	}
}
