package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionPlan;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.preparation.ExecutionPreparation;
import org.jetbrains.annotations.NotNull;

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
		ExecutionProvider execution = executions.get(plan.getExecutionId());
		if (execution == null)
			throw new IllegalArgumentException("No execution provider '" + plan.getExecutionId() + "'. Available: " + executions.keySet());
		List<List<ProcessSpec>> order = startupOrder(plan);
		ManagedProcessGroup group = new ManagedProcessGroup(plan, execution, preparation, order);
		try {
			group.start();
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

	private List<List<ProcessSpec>> startupOrder(ExecutionPlan plan) {
		if (plan.getParallelism() < 1 || plan.getStartupMemoryMegabytes() < 1)
			throw new IllegalArgumentException("Startup limits must be positive");
		if (plan.getStartupTimeout().isNegative() || plan.getStartupTimeout().isZero()
				|| plan.getStopTimeout().isNegative() || plan.getStopTimeout().isZero())
			throw new IllegalArgumentException("Process timeouts must be positive");

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
