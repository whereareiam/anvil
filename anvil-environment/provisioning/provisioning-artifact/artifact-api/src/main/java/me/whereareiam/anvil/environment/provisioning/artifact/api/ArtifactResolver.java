package me.whereareiam.anvil.environment.provisioning.artifact.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Obtains verified artifacts and resolution metadata according to the configured acquisition policy.
 *
 * <p>Providers borrow this service during preparation. The resolver owns downloads,
 * cache coordination, and checksum verification.</p>
 */
public interface ArtifactResolver {
	/**
	 * Returns a cached artifact, obtaining and verifying it when matching content is unavailable.
	 * An unsuccessful download or verification leaves any previous destination intact.
	 *
	 * @param uri            remote artifact location
	 * @param destination    destination inside the Anvil cache
	 * @param expectedSha256 expected artifact SHA-256, or {@code null} when the provider permits unpinned content
	 * @return verified local artifact
	 */
	@NotNull Path obtain(
			@NotNull URI uri,
			@NotNull Path destination,
			@Nullable String expectedSha256
	);

	/**
	 * Reads cached resolution metadata, refreshing it only when the configured policy requests it.
	 *
	 * @param uri resource location
	 * @return response body
	 */
	@NotNull String read(@NotNull URI uri);
}
