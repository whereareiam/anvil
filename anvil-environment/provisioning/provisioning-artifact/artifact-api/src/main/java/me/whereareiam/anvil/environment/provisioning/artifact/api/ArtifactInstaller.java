package me.whereareiam.anvil.environment.provisioning.artifact.api;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Produces an installed directory, such as the server a platform's installer writes, for
 * {@link ArtifactResolver#install(Path, ArtifactInstaller)} to publish.
 */
@FunctionalInterface
public interface ArtifactInstaller {
	/**
	 * Writes the complete installation into the supplied empty directory, which is published only when this
	 * returns normally and discarded otherwise.
	 *
	 * @param staging empty, unpublished directory owned by the installation
	 * @throws IOException when the installation cannot be completed
	 */
	void install(@NotNull Path staging) throws IOException;
}
