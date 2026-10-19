package me.whereareiam.anvil.environment.execution.managed;

import lombok.experimental.Delegate;
import me.whereareiam.anvil.api.model.process.ProcessAddresses;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionPlan;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.preparation.ExecutionPreparation;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.execution.managed.slot.ProcessSlot;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns execution targets, prepared inputs, and generations from acquisition through finalization.
 */
final class ManagedProcessGroup implements ProcessGroup {
	private final ExecutionPlan plan;
	/**
	 * The execution providers the plan's processes run with, by identifier.
	 */
	private final Map<String, ExecutionProvider> executions;
	private final ExecutionPreparation preparation;
	private final List<List<ProcessSpec>> startupOrder;
	private final @Nullable ScenarioObserver observer;
	private final Map<String, ProcessTarget> targets = new ConcurrentHashMap<>();
	@Delegate(types = ScenarioProcesses.class, excludes = CompleteStartup.class)
	private final ProcessRegistry processes = new ProcessRegistry();
	/**
	 * The open session of each execution provider in use, in the order they opened.
	 */
	private final Map<String, ExecutionSession> sessions = new LinkedHashMap<>();
	private boolean closed;

	ManagedProcessGroup(
			ExecutionPlan plan,
			Map<String, ExecutionProvider> executions,
			ExecutionPreparation preparation,
			List<List<ProcessSpec>> startupOrder,
			@Nullable ScenarioObserver observer
	) {
		this.plan = plan;
		this.executions = Map.copyOf(executions);
		this.preparation = preparation;
		this.startupOrder = startupOrder;
		this.observer = observer;
	}

	void prepare() {
		preparation.open();
		for (ProcessSpec process : plan.getProcesses())
			sessions.computeIfAbsent(executionOf(process), id -> executions.get(id).open(plan.getContext()));

		ProcessScheduler tasks = new ProcessScheduler(plan.getProcessScheduling().getParallelism(), plan.getProcessScheduling().getStartupMemoryMegabytes());
		tasks.run(plan.getProcesses(), process -> {
			ProcessTarget target = sessions.get(executionOf(process)).prepare(process.getRequest());
			targets.put(process.getRequest().getName(), target);
		});

		for (ProcessSpec process : orderedProcesses()) {
			String name = process.getRequest().getName();
			Map<String, InetSocketAddress> peers = peersOf(process);
			processes.register(new ProcessSlot(process, targets.get(name), preparation, peers, addressesOf(process, peers),
					plan.getProcessTimeouts().getStartup(), plan.getProcessTimeouts().getShutdown(), observer));
		}

		tasks.run(plan.getProcesses(), process -> processes.prepare(process.getRequest().getName()));
	}

	private String executionOf(ProcessSpec process) {
		return process.getExecutionProviderId() == null ? plan.getExecutionProviderId() : process.getExecutionProviderId();
	}

	/**
	 * Returns where a process reaches each other process: inside their environment when both run in the same
	 * execution session, else at the other's host address, together with the running peers outside the plan.
	 */
	private Map<String, InetSocketAddress> peersOf(ProcessSpec reader) {
		Map<String, InetSocketAddress> peers = new LinkedHashMap<>(plan.getPeers());
		for (ProcessSpec process : plan.getProcesses()) {
			ProcessTarget target = targets.get(process.getRequest().getName());
			peers.put(process.getRequest().getName(), sameSession(reader, process) ? target.peerAddress() : target.address());
		}

		return Map.copyOf(peers);
	}

	private ProcessAddresses addressesOf(ProcessSpec reader, Map<String, InetSocketAddress> peers) {
		Map<String, Map<String, InetSocketAddress>> listeners = new LinkedHashMap<>();
		for (ProcessSpec process : plan.getProcesses()) {
			ProcessTarget target = targets.get(process.getRequest().getName());
			listeners.put(process.getRequest().getName(), sameSession(reader, process) ? target.peerListeners() : target.listeners());
		}

		return ProcessAddresses.of(peers, listeners);
	}

	private boolean sameSession(ProcessSpec reader, ProcessSpec process) {
		return executionOf(reader).equals(executionOf(process));
	}

	@Override
	public void startAll() {
		synchronized (this) {
			if (closed) throw new IllegalStateException("Cannot start a finalized process group");
		}
		ProcessScheduler tasks = new ProcessScheduler(plan.getProcessScheduling().getParallelism(), plan.getProcessScheduling().getStartupMemoryMegabytes());
		for (List<ProcessSpec> layer : startupOrder)
			tasks.start(layer, process -> processes.start(process.getRequest().getName()));
	}

	@Override
	public void finish(boolean successful) {
		// Checked before any teardown or the monitor, so a rejected call leaves the group intact.
		processes.rejectNestedChange("finish the process group");
		finishOwned(successful);
	}

	private synchronized void finishOwned(boolean successful) {
		if (closed) return;
		closed = true;

		List<Throwable> failures = new ArrayList<>();
		attempt(() -> processes.stop(startupOrder.stream()
				.map(layer -> layer.stream().map(process -> process.getRequest().getName()).toList()).toList()), failures);
		for (ProcessSpec process : orderedProcesses().reversed()) {
			ProcessTarget target = targets.remove(process.getRequest().getName());
			if (target != null) attempt(target::close, failures);
		}
		for (ExecutionSession session : List.copyOf(sessions.values()).reversed()) attempt(session::close, failures);
		attempt(() -> processes.finish(successful && failures.isEmpty()), failures);
		attempt(() -> preparation.finish(successful && !processes.failed() && failures.isEmpty()), failures);
		if (failures.isEmpty()) return;

		Throwable first = failures.getFirst();
		for (Throwable failure : failures.subList(1, failures.size()))
			if (failure != first) first.addSuppressed(failure);
		if (first instanceof Error error) throw error;
		throw (RuntimeException) first;
	}

	private List<ProcessSpec> orderedProcesses() {
		return startupOrder.stream().flatMap(List::stream).toList();
	}

	private void attempt(Runnable action, List<Throwable> failures) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failures.add(failure);
		}
	}
	private interface CompleteStartup {
		void startAll();
	}

}
