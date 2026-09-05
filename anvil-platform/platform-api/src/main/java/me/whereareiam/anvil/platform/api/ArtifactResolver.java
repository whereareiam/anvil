package me.whereareiam.anvil.platform.api;

import me.whereareiam.anvil.platform.api.model.PlatformContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Resolves immutable platform artifacts through Anvil's shared cache.
 *
 * <p>Platform providers receive this service through {@link PlatformContext}; they do not need
 * to implement download locking, atomic writes, or checksum verification themselves.</p>
 */
public interface ArtifactResolver {
	/**
	 * Returns a cached artifact, obtaining and verifying it when it is not cached yet.
	 *
	 * @param uri remote artifact location
	 * @param destination destination inside the Anvil cache
	 * @param expectedSha256 expected artifact SHA-256, or {@code null} when the provider permits unpinned content
	 * @return verified local artifact
	 */
	@NotNull Path obtain(
			@NotNull URI uri,
			@NotNull Path destination,
			@Nullable String expectedSha256
	);

	/**
	 * Reads a small remote resource without persisting it as an artifact.
	 *
	 * @param uri resource location
	 * @return response body
	 */
	@NotNull String read(@NotNull URI uri);
}
