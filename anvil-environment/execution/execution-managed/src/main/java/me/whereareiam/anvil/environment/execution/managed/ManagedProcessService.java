package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionPlan;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.preparation.ExecutionPreparation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes prepared process plans with explicit input and generation lifecycle ownership.
 */
public final class ManagedProcessService {
	private final Map<String, ExecutionProvider> executions;

	public ManagedProcessService(@NotNull Map<String, ExecutionProvider> executions) {
		this.executions = Map.copyOf(executions);
	}

	/**
	 * Starts every process in dependency order and transfers the completed group to its caller.
	 * Invalid plans fail before preparation is opened; partial startup releases all acquired owners.
	 *
	 * @param plan execution-owned values and selected provider
	 * @param preparation preparation and finalization of inputs and launch attachments
	 * @return caller-owned running group
	 */
	public @NotNull ProcessGroup start(@NotNull ExecutionPlan plan, @NotNull ExecutionPreparation preparation) {
		ProcessGroup group = prepare(plan, preparation, null);
		try {
			group.startAll();
			return group;
		} catch (RuntimeException | Error failure) {
			try { group.finish(false); }
			catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
			throw failure;
		}
	}

	/**
	 * Allocates the complete topology and prepares every input without launching a process.
	 *
	 * @param plan validated execution requirements
	 * @param preparation owned input and launch preparation
	 * @param observer optional borrowed-generation observer
	 * @return owned prepared process group
	 */
	public @NotNull ProcessGroup prepare(@NotNull ExecutionPlan plan, @NotNull ExecutionPreparation preparation, @Nullable ScenarioObserver observer) {
		ExecutionProvider execution = provider(plan.getExecutionProviderId());
		List<List<ProcessSpec>> order = startupOrder(plan);
		ManagedProcessGroup group = new ManagedProcessGroup(plan, execution, preparation, order, observer);
		try {
			group.prepare();
			return group;
		} catch (RuntimeException | Error failure) {
			try {
				group.finish(false);
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	/**
	 * Returns whether processes of one execution provider see a game connection's own source address.
	 *
	 * @param executionProviderId selected execution provider
	 * @return whether the provider preserves client addresses
	 * @throws IllegalArgumentException when no such provider is installed
	 */
	public boolean preservesClientAddress(@NotNull String executionProviderId) {
		return provider(executionProviderId).preservesClientAddress();
	}

	private ExecutionProvider provider(String executionProviderId) {
		ExecutionProvider execution = executions.get(executionProviderId);
		if (execution == null)
			throw new IllegalArgumentException("No execution provider '" + executionProviderId + "'. Available: " + executions.keySet());
		return execution;
	}

	private List<List<ProcessSpec>> startupOrder(ExecutionPlan plan) {
		var scheduling = plan.getProcessScheduling();
		if (scheduling.getParallelism() == null || scheduling.getStartupMemoryMegabytes() == null
				|| scheduling.getParallelism() < 1 || scheduling.getStartupMemoryMegabytes() < 1)
			throw new IllegalArgumentException("Process scheduling limits must be resolved and positive");
		var timeouts = plan.getProcessTimeouts();
		if (timeouts.getStartup() == null || timeouts.getShutdown() == null
				|| timeouts.getStartup().isNegative() || timeouts.getStartup().isZero()
				|| timeouts.getShutdown().isNegative() || timeouts.getShutdown().isZero())
			throw new IllegalArgumentException("Process timeouts must be resolved and positive");

		Map<String, ProcessSpec> remaining = new LinkedHashMap<>();
		for (ProcessSpec process : plan.getProcesses()) {
			String name = process.getRequest().getName();
			if (remaining.putIfAbsent(name, process) != null)
				throw new IllegalArgumentException("Duplicate process: " + name);
			if (process.getMemoryMegabytes() < 1)
				throw new IllegalArgumentException("Process memory must be positive: " + name);
		}
		for (ProcessSpec process : remaining.values())
			if (!remaining.keySet().containsAll(process.getDependencies()))
				throw new IllegalArgumentException("Unknown startup dependency for process '" + process.getRequest().getName() + "'");

		Set<String> started = new HashSet<>();
		List<List<ProcessSpec>> order = new ArrayList<>();
		while (!remaining.isEmpty()) {
			List<ProcessSpec> ready = remaining.values().stream()
					.filter(process -> started.containsAll(process.getDependencies())).toList();
			if (ready.isEmpty()) throw new IllegalArgumentException("Cyclic process startup dependencies: " + remaining.keySet());
			order.add(ready);
			for (ProcessSpec process : ready) {
				String name = process.getRequest().getName();
				remaining.remove(name);
				started.add(name);
			}
		}

		return List.copyOf(order);
	}
}
