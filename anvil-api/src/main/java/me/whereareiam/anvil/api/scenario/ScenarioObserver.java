package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.process.RunningProcess;
import org.jetbrains.annotations.NotNull;

/**
 * Observes process generations acquired by an engine-owned scenario, including replacements.
 * Independent processes may invoke the observer concurrently during parallel startup.
 * An observer receives borrowed handles and does not acquire ownership of scenario resources.
 */
public interface ScenarioObserver {
	/**
	 * Receives a process in its CREATED state before its native execution starts.
	 * Return promptly: launch begins after this callback returns, so the callback must not wait
	 * for readiness or output. Retain the handle to inspect its state and console from another
	 * thread; console history remains readable after a failed start or completed cleanup.
	 * Each restart supplies a new handle, leaving the previous generation's identity unchanged.
	 * <p>
	 * The callback runs inside the process start. It must not finish the scenario, close the engine,
	 * or start, stop, or restart processes; those calls are rejected with {@link IllegalStateException}.
	 * To abort startup, throw from the callback instead.
	 *
	 * @param process borrowed process generation, with its address, workspace, and console available
	 * @throws RuntimeException if observation cannot be established; the engine rolls back startup
	 */
	void processCreated(@NotNull RunningProcess process);
}
