package me.whereareiam.anvil.environment.execution.api;

import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import org.jetbrains.annotations.NotNull;

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
	 * Releases the environment after process cleanup, including partially prepared resources.
	 */
	@Override
	void close();
}
