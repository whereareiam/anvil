package me.whereareiam.anvil.agent.api.transport;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Resolves the packaged agent artifact represented by a platform agent entrypoint.
 *
 * <p>Artifact discovery belongs to the agent runtime because the host must not know how a
 * provider or platform agent was packaged.</p>
 */
public interface AgentArtifactLocator {
	/**
	 * Locates the runnable agent JAR for a loaded entrypoint class.
	 *
	 * @param entrypointClassName platform agent entrypoint
	 * @return runnable agent artifact
	 */
	@NotNull Path locate(@NotNull String entrypointClassName);
}
