package me.whereareiam.anvil.engine.scenario.process;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Bounds independent startup work, stops queued work after failure, and settles every active owner.
 */
public final class StartupScheduler {
	private final int parallelism;
	private final int memoryBudget;

	public StartupScheduler(int parallelism, int memoryBudget) {
		if (parallelism < 1 || memoryBudget < 1) throw new IllegalArgumentException("Startup limits must be positive");
		this.parallelism = parallelism;
		this.memoryBudget = memoryBudget;
	}

	public <T> void run(Collection<T> inputs, Consumer<T> action) {
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

	public <T extends MinecraftProcess> void start(Collection<T> inputs, Consumer<T> action) {
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
