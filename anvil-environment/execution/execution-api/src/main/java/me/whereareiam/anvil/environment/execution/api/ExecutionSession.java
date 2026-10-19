package me.whereareiam.anvil.environment.execution.api;

import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns execution-specific resources shared by the processes of one scenario.
 */
public interface ExecutionSession extends AutoCloseable {
	/**
	 * Prepares endpoints and Java for a process without launching its executable JAR.
	 *
	 * @param request declaration and host staging directory
	 * @return execution target retained across process replacements
	 */
	@NotNull ProcessTarget prepare(@NotNull ProcessRequest request);

	/**
	 * Returns the container network the session's processes run in. Containers the scenario starts beside its
	 * processes, such as its services, join it and are reached there by name.
	 *
	 * @return the network's identifier, or null when the processes run on the host and reach such containers at
	 * their published host ports
	 */
	default @Nullable String containerNetwork() {
		return null;
	}

	/**
	 * Releases the environment after process cleanup, including partially prepared resources.
	 */
	@Override
	void close();
}
