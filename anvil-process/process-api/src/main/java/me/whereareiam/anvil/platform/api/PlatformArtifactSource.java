package me.whereareiam.anvil.platform.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Obtains platform distribution artifacts and the metadata used to select them.
 * Providers own vendor selection and destination layout; acquisition policy is supplied externally.
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
	 * Reads platform catalog metadata according to the configured refresh/offline policy.
	 * @param uri provider-selected catalog document
	 * @return catalog content
	 */
	@NotNull String read(@NotNull URI uri);
}
