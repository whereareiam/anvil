package me.whereareiam.anvil.capability.process;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Finalizes logical capabilities before their underlying process group, preserving failed outcomes.
 */
@RequiredArgsConstructor
final class CapabilityProcessGroup implements ProcessGroup {
	@Delegate(types = ScenarioProcesses.class)
	private final @NotNull ProcessGroup processes;
	private final @NotNull Map<String, ProcessCapabilities> capabilities;

	private boolean closed;

	@Override
	public void startAll() {
		processes.startAll();
	}

	@Override
	public void finish(boolean successful) {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		Throwable failure = ProcessCapabilityRuntime.close(capabilities.values(), null);
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
