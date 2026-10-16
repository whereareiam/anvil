package me.whereareiam.anvil.environment.provisioning.artifact.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.function.Predicate;

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
	default @NotNull String read(@NotNull URI uri) {
		return read(uri, body -> true);
	}

	/**
	 * Reads cached resolution metadata like {@link #read(URI)}, keeping only a response the caller can use.
	 * A cached response that is not usable is downloaded again, and a downloaded response that is not usable is
	 * neither cached nor returned, so a temporary empty answer of a remote service does not outlive its request.
	 * Reading fails when no usable response is cached and none can be downloaded, such as while offline.
	 *
	 * @param uri    resource location
	 * @param usable whether a response answers the caller's request, such as a search that found something
	 * @return usable response body
	 */
	@NotNull String read(@NotNull URI uri, @NotNull Predicate<String> usable);
}
