package me.whereareiam.anvil.provisioning.api.artifact;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Resolves immutable platform artifacts through Anvil's shared cache.
 *
 * <p>Platform providers receive this service during preparation; they do not need
 * to implement download locking, atomic writes, or checksum verification themselves.</p>
 */
public interface ArtifactResolver {
    /**
     * Returns a cached artifact, obtaining and verifying it when it is not cached yet.
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
