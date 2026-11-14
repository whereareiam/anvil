package me.whereareiam.anvil.capability.process;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Finalizes logical capabilities before their underlying process group, preserving failed outcomes. A scenario's
 * group reports {@code ScenarioStarted} to the capabilities once every process is ready, and {@code ScenarioFinished}
 * before closing them; a failed action fails the scenario's start or cleanup.
 */
@RequiredArgsConstructor
final class CapabilityProcessGroup implements ProcessGroup {
	@Delegate(types = ScenarioProcesses.class)
	private final @NotNull ProcessGroup processes;
	private final @NotNull Map<String, ProcessCapabilities> capabilities;
	/**
	 * Whether starting and finishing the group start and finish a scenario, as they do for every group but one kept
	 * beyond its scenarios.
	 */
	private final boolean scenario;

	private boolean closed;

	@Override
	public void startAll() {
		processes.startAll();
		if (scenario) ProcessCapabilityRuntime.rethrow(ProcessCapabilityRuntime.startScenario(capabilities.values(), null));
	}

	@Override
	public void finish(boolean successful) {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		Throwable failure = scenario ? ProcessCapabilityRuntime.finishScenario(capabilities.values(), successful, null) : null;
		failure = ProcessCapabilityRuntime.close(capabilities.values(), failure);
		try {
			processes.finish(successful && capabilities.values().stream().noneMatch(ProcessCapabilities::failed) && failure == null);
		} catch (RuntimeException | Error cleanup) {
			if (failure == null) failure = cleanup;
			else if (failure != cleanup) failure.addSuppressed(cleanup);
		}

		if (failure instanceof RuntimeException runtime) throw runtime;
		if (failure instanceof Error error) throw error;
	}
}
