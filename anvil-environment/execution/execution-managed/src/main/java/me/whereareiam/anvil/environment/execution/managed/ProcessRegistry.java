package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.type.RunningProxy;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.environment.execution.managed.slot.ProcessSlot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns a run's prepared process collection, current-generation access, and process finalization.
 * The scenario session synchronizes on this owner when closing players before the process group.
 */
public final class ProcessRegistry {
	private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
	private final Map<String, ProcessSlot> processes = new LinkedHashMap<>();
	private volatile boolean failed;
	private volatile boolean stopping;
	private boolean closed;
	private boolean finalized;

	/**
	 * Takes ownership before preparation so a failed launch can be finalized with the group.
	 *
	 * @param process prepared process inputs
	 */
	public synchronized void register(@NotNull ProcessSlot process) {
		if (closed) throw new IllegalStateException("Cannot add a process after scenario cleanup");
		if (processes.putIfAbsent(process.name(), process) != null)
			throw new IllegalArgumentException("Duplicate process: " + process.name());
	}

	public void prepare(@NotNull String name) {
		execute(() -> require(name).prepare());
	}

	public @NotNull RunningProcess start(@NotNull String name) {
		return mutate(name, process -> { }, ProcessSlot::start);
	}

	public void stop(@NotNull String name) {
		mutate(name, process -> { }, process -> {
			if (process.currentOrNull() != null) process.stopGeneration();
			return null;
		});
	}

	/**
	 * Applies a lifecycle action to one process. Caller mistakes, such as an unknown name or a violated
	 * precondition, are reported without failing the run; failures of the action itself fail it.
	 */
	private <T> T mutate(
			@NotNull String name,
			@NotNull Consumer<ProcessSlot> precondition,
			@NotNull Function<ProcessSlot, T> action
	) {
		rejectNestedChange("change processes");
		if (stopping) throw new IllegalStateException("Cannot change processes while the scenario is finishing");

		lifecycle.readLock().lock();
		try {
			if (closed) throw new IllegalStateException("Cannot change processes after scenario cleanup");

			ProcessSlot process = require(name);
			precondition.accept(process);
			recordFailures();
			try {
				return action.apply(process);
			} catch (RuntimeException | Error failure) {
				failed = true;
				throw failure;
			}
		} finally {
			lifecycle.readLock().unlock();
		}
	}

	private void execute(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failed = true;
			throw failure;
		}
	}

	public synchronized @NotNull Collection<RunningProcess> all() {
		return processes.values().stream()
				.map(ProcessSlot::currentOrNull)
				.filter(Objects::nonNull)
				.map(RunningProcess.class::cast)
				.toList();
	}

	public synchronized @NotNull RunningProcess get(@NotNull String name) {
		return require(name).current();
	}

	public synchronized @NotNull Collection<RunningServer> servers() {
		return processes.values().stream()
				.map(ProcessSlot::currentOrNull)
				.filter(RunningServer.class::isInstance)
				.map(RunningServer.class::cast)
				.toList();
	}

	public synchronized @NotNull RunningServer server(@NotNull String name) {
		RunningProcess process = get(name);
		if (!(process instanceof RunningServer server))
			throw new IllegalArgumentException("Process '" + name + "' is not a Minecraft server");

		return server;
	}

	public synchronized @NotNull Collection<RunningProxy> proxies() {
		return processes.values().stream()
				.map(ProcessSlot::currentOrNull)
				.filter(RunningProxy.class::isInstance)
				.map(RunningProxy.class::cast)
				.toList();
	}

	public synchronized @NotNull RunningProxy proxy(@NotNull String name) {
		RunningProcess process = get(name);
		if (!(process instanceof RunningProxy proxy))
			throw new IllegalArgumentException("Process '" + name + "' is not a Minecraft proxy");

		return proxy;
	}

	public @NotNull RunningProcess restart(@NotNull String name) {
		return mutate(name, process -> {
			if (process.currentOrNull() == null) throw new IllegalStateException("Process has not started: " + name);
		}, ProcessSlot::restart);
	}

	/**
	 * Reports whether startup, restart, or cleanup has failed.
	 *
	 * @return whether this collection prevents successful run finalization
	 */
	public synchronized boolean failed() {
		recordFailures();
		return failed;
	}

	private void recordFailures() {
		if (processes.values().stream().anyMatch(ProcessSlot::failed)) failed = true;
	}

	/**
	 * Rejects an action requested by a thread that is itself starting or changing a process, such as a
	 * process observer. Such an action would wait for the change that is running it.
	 *
	 * @param action description of the rejected action, such as "finish the process group"
	 * @throws IllegalStateException when the current thread is inside a process change
	 */
	public void rejectNestedChange(@NotNull String action) {
		if (lifecycle.getReadHoldCount() > 0)
			throw new IllegalStateException("Cannot " + action + " while this thread is starting or changing a process");
	}

	/**
	 * Closes launch attachments and JVMs, retaining workspaces until execution resources are released.
	 * Processes stop in reverse startup order; those that started together stop together.
	 *
	 * @param startupLayers names of the registered processes, grouped by the layer they start in
	 */
	public void stop(@NotNull List<List<String>> startupLayers) {
		rejectNestedChange("stop processes");
		// Starts in progress hold the read lock until readiness; cancel them instead of waiting.
		stopping = true;
		slots().forEach(ProcessSlot::cancelStart);
		lifecycle.writeLock().lock();

		try {
			stopAll(startupLayers);
		} finally {
			lifecycle.writeLock().unlock();
		}
	}

	private synchronized List<ProcessSlot> slots() {
		return List.copyOf(processes.values());
	}

	private void stopAll(List<List<String>> startupLayers) {
		if (closed) return;
		recordFailures();
		closed = true;

		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
		processes.values().forEach(process -> attempt(process::closeLaunch, failures));
		for (List<String> layer : startupLayers.reversed())
			// A failed preparation stops before every planned process was registered.
			stopTogether(layer.reversed().stream().map(processes::get).filter(Objects::nonNull).toList(), failures);
		report(failures);
	}

	/**
	 * Stops independent processes at once and waits for all of them, so one slow shutdown does not delay the others.
	 */
	private void stopTogether(List<ProcessSlot> layer, List<Throwable> failures) {
		if (layer.size() < 2) {
			layer.forEach(process -> attempt(process::stopProcess, failures));
			return;
		}

		try (ExecutorService executor = Executors.newFixedThreadPool(layer.size())) {
			for (ProcessSlot process : layer)
				executor.execute(() -> attempt(process::stopProcess, failures));
		}
	}

	/**
	 * Finalizes workspaces after every execution target and the execution session were released.
	 *
	 * @param successful whether scenario execution and execution-resource cleanup succeeded
	 */
	public synchronized void finish(boolean successful) {
		if (finalized) return;
		if (!closed) throw new IllegalStateException("Processes must stop before finalizing their workspaces");

		recordFailures();
		finalized = true;

		List<Throwable> failures = new ArrayList<>();
		List<ProcessSlot> reverse = new ArrayList<>(processes.values()).reversed();
		reverse.forEach(process -> attempt(() -> process.finish(successful && !failed && failures.isEmpty()), failures));
		report(failures);
	}

	private void report(List<Throwable> failures) {
		if (failures.isEmpty()) return;

		failed = true;
		Throwable first = failures.getFirst();
		for (Throwable failure : failures.subList(1, failures.size()))
			if (failure != first) first.addSuppressed(failure);

		if (first instanceof Error error) throw error;
		throw (RuntimeException) first;
	}

	private ProcessSlot require(String name) {
		ProcessSlot process = processes.get(name);
		if (process == null) {
			throw new NoSuchElementException("Unknown process '" + name + "'. Available: " + processes.keySet());
		}

		return process;
	}

	private void attempt(Runnable action, List<Throwable> failures) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failures.add(failure);
		}
	}
}
