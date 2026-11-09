package me.whereareiam.anvil.platform.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Obtains platform distribution artifacts and the metadata used to select them.
 * Providers own vendor selection and destination layout; acquisition policy is supplied externally.
 *
 * <p>Paths it accepts as destinations and returns are the workbench's: they name files on the machine that runs
 * the process, below {@link me.whereareiam.anvil.platform.api.model.PlatformContext#getCacheDirectory()}. A file of
 * the engine's own machine, such as a local distribution JAR, reaches the workbench through {@link #store(Path)}.</p>
 */
public interface PlatformArtifactSource {
	/**
	 * Obtains the selected platform artifact, verifying its checksum when supplied.
	 * An unsuccessful acquisition preserves previous destination contents.
	 *
	 * @param uri provider-selected distribution artifact
	 * @param destination provider-selected local artifact path
	 * @param sha256 expected content checksum, or null when the distribution permits an unpinned artifact
	 * @return prepared local artifact
	 */
	@NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String sha256);

	/**
	 * Returns a platform installation in the cache, running the installer when it is absent. Scenarios that prepare
	 * one destination at once, in this JVM or in others sharing the cache, install it once; the others wait and use
	 * the published installation. It is published as a whole after the installer returns, so an existing
	 * destination is complete, and a failed installation leaves nothing behind.
	 *
	 * @param destination provider-selected installation directory below the cache directory
	 * @param installer   writes the complete installation into an empty staging directory
	 * @return installed directory
	 */
	@NotNull Path install(@NotNull Path destination, @NotNull PlatformInstaller installer);

	/**
	 * Reads platform catalog metadata according to the configured refresh/offline policy.
	 * @param uri provider-selected catalog document
	 * @return catalog content
	 */
	@NotNull String read(@NotNull URI uri);

	/**
	 * Makes a file or directory of the engine's machine available to the process, such as a local distribution
	 * JAR, and returns where the workbench keeps it. The default keeps it where it is, which is right when the
	 * process runs on the engine's machine.
	 *
	 * @param file existing file or directory on the engine's machine
	 * @return the same content on the workbench
	 */
	default @NotNull Path store(@NotNull Path file) {
		return file;
	}
}
