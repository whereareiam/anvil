package me.whereareiam.anvil.integration.intellij.scenario.execution;

import lombok.experimental.UtilityClass;
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;

/**
 * Calculates the effective environment state from its retained runtime snapshot.
 */
@UtilityClass
public final class EnvironmentStateCalculator {
	public static @NotNull EnvironmentState calculate(
			@NotNull ScenarioDescriptor scenario,
			@NotNull SessionSnapshot snapshot
	) {
		switch (snapshot.getState()) {
			case IDLE: return EnvironmentState.NOT_STARTED;
			case STARTING: return EnvironmentState.STARTING;
			case STOPPING: return EnvironmentState.STOPPING;
			case STOPPED: return EnvironmentState.STOPPED;
			case FAILED: return EnvironmentState.FAILED;
			case RUNNING: break;
		}

		if (snapshot.getProcesses().stream().anyMatch(process -> process.getState() == ProcessState.FAILED))
			return EnvironmentState.FAILED;
		if (snapshot.getProcesses().stream().anyMatch(process -> process.getState() == ProcessState.STOPPING))
			return EnvironmentState.STOPPING;
		if (snapshot.getProcesses().stream().anyMatch(process -> process.getState() == ProcessState.STARTING))
			return EnvironmentState.STARTING;

		long ready = snapshot.getProcesses().stream()
				.filter(process -> process.getState() == ProcessState.READY).count();
		if (ready == 0) return EnvironmentState.READY_TO_START;
		if (ready < Math.max(scenario.getProcesses().size(), snapshot.getProcesses().size()))
			return EnvironmentState.PARTIALLY_RUNNING;
		if (scenario.isSetupAvailable() && !snapshot.isSetupComplete())
			return EnvironmentState.SETUP_PENDING;

		return EnvironmentState.RUNNING;
	}
}
