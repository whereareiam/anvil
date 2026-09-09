package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.process.type.RunningProxy;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.environment.execution.managed.slot.ProcessSlot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Owns a run's prepared process collection, current-generation access, and process finalization.
 * The scenario session synchronizes on this owner when closing players before the process group.
 */
public final class ProcessRegistry implements ScenarioProcesses {
	private final Map<String, ProcessSlot> processes = new LinkedHashMap<>();
	private volatile boolean failed;
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

	public void configure(@NotNull String name) {
		execute(() -> require(name).configure());
	}

	public void start(@NotNull String name) {
		execute(() -> require(name).start());
	}

	private void execute(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failed = true;
			throw failure;
		}
	}

	@Override
	public synchronized @NotNull Collection<RunningProcess> all() {
		return processes.values().stream().map(process -> (RunningProcess) process.current()).toList();
	}

	@Override
	public synchronized @NotNull RunningProcess get(@NotNull String name) {
		return require(name).current();
	}

	@Override
	public synchronized @NotNull Collection<RunningServer> servers() {
		return processes.values().stream()
				.map(ProcessSlot::current)
				.filter(RunningServer.class::isInstance)
				.map(RunningServer.class::cast)
				.toList();
	}

	@Override
	public synchronized @NotNull RunningServer server(@NotNull String name) {
		RunningProcess process = get(name);
		if (!(process instanceof RunningServer server))
			throw new IllegalArgumentException("Process '" + name + "' is not a Minecraft server");

		return server;
	}

	@Override
	public synchronized @NotNull Collection<RunningProxy> proxies() {
		return processes.values().stream()
				.map(ProcessSlot::current)
				.filter(RunningProxy.class::isInstance)
				.map(RunningProxy.class::cast)
				.toList();
	}

	@Override
	public synchronized @NotNull RunningProxy proxy(@NotNull String name) {
		RunningProcess process = get(name);
		if (!(process instanceof RunningProxy proxy))
			throw new IllegalArgumentException("Process '" + name + "' is not a Minecraft proxy");

		return proxy;
	}

	@Override
	public synchronized @NotNull RunningProcess restart(@NotNull String name) {
		if (closed) throw new IllegalStateException("Cannot restart a closed scenario");
		ProcessSlot process = require(name);
		try {
			return process.restart();
		} catch (RuntimeException | Error failure) {
			failed = true;
			throw failure;
		}
	}

	/**
	 * Reports whether startup, restart, or cleanup has failed.
	 *
	 * @return whether this collection prevents successful run finalization
	 */
	public synchronized boolean failed() {
		return failed;
	}

	/**
	 * Closes launch attachments and JVMs, retaining workspaces until execution resources are released.
	 */
	public synchronized void stop() {
		if (closed) return;
		closed = true;

		List<Throwable> failures = new ArrayList<>();
		processes.values().forEach(process -> attempt(process::closeLaunch, failures));
		List<ProcessSlot> reverse = new ArrayList<>(processes.values()).reversed();
		reverse.forEach(process -> attempt(process::stopProcess, failures));
		report(failures);
	}

	/**
	 * Finalizes workspaces after every execution target and the execution session were released.
	 *
	 * @param successful whether scenario execution and execution-resource cleanup succeeded
	 */
	public synchronized void finish(boolean successful) {
		if (finalized) return;
		if (!closed) throw new IllegalStateException("Processes must stop before finalizing their workspaces");
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
		if (process == null) throw new NoSuchElementException("Unknown process '" + name + "'. Available: " + processes.keySet());
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
