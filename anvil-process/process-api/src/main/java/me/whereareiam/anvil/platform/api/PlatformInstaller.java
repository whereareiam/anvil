package me.whereareiam.anvil.platform.api;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Installs a platform's server, such as by running its vendor installer, for
 * {@link PlatformArtifactSource#install(Path, PlatformInstaller)} to publish.
 */
@FunctionalInterface
public interface PlatformInstaller {
	/**
	 * Writes the complete installation into the supplied empty directory, which is published only when this
	 * returns normally and discarded otherwise.
	 *
	 * @param staging empty, unpublished directory owned by the installation
	 * @throws IOException when the installation cannot be completed
	 */
	void install(@NotNull Path staging) throws IOException;
}
