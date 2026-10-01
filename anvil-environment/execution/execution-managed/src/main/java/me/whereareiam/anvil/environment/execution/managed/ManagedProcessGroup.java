package me.whereareiam.anvil.environment.execution.managed;

import lombok.experimental.Delegate;
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
	private final ExecutionProvider execution;
	private final ExecutionPreparation preparation;
	private final List<List<ProcessSpec>> startupOrder;
	private final @Nullable ScenarioObserver observer;
	private final Map<String, ProcessTarget> targets = new ConcurrentHashMap<>();
	@Delegate(types = ScenarioProcesses.class, excludes = CompleteStartup.class)
	private final ProcessRegistry processes = new ProcessRegistry();
	private @Nullable ExecutionSession session;
	private boolean closed;

	ManagedProcessGroup(
			ExecutionPlan plan,
			ExecutionProvider execution,
			ExecutionPreparation preparation,
			List<List<ProcessSpec>> startupOrder,
			@Nullable ScenarioObserver observer
	) {
		this.plan = plan;
		this.execution = execution;
		this.preparation = preparation;
		this.startupOrder = startupOrder;
		this.observer = observer;
	}

	void prepare() {
		preparation.open();
		ExecutionSession opened = execution.open(plan.getContext());
		session = opened;
		ProcessScheduler tasks = new ProcessScheduler(plan.getProcessScheduling().getParallelism(), plan.getProcessScheduling().getStartupMemoryMegabytes());
		tasks.run(plan.getProcesses(), process -> {
			ProcessTarget target = opened.prepare(process.getRequest());
			targets.put(process.getRequest().getName(), target);
		});

		Map<String, InetSocketAddress> addresses = new LinkedHashMap<>();
		for (ProcessSpec process : plan.getProcesses()) {
			String name = process.getRequest().getName();
			addresses.put(name, targets.get(name).peerAddress());
		}
		Map<String, InetSocketAddress> peers = Map.copyOf(addresses);
		for (ProcessSpec process : orderedProcesses()) {
			String name = process.getRequest().getName();
			processes.register(new ProcessSlot(process, targets.get(name), preparation, peers,
					plan.getProcessTimeouts().getStartup(), plan.getProcessTimeouts().getShutdown(), observer));
		}

		tasks.run(plan.getProcesses(), process -> processes.prepare(process.getRequest().getName()));
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
		attempt(processes::stop, failures);
		for (ProcessSpec process : orderedProcesses().reversed()) {
			ProcessTarget target = targets.remove(process.getRequest().getName());
			if (target != null) attempt(target::close, failures);
		}
		if (session != null) attempt(session::close, failures);
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
