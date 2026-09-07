package me.whereareiam.anvil.provisioning.api.artifact;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Shared storage for verified downloads, resolution metadata, and prepared installations.
 */
public interface ArtifactStore extends ArtifactResolver, AutoCloseable {
    /**
     * Acquires exclusive entry ownership before inspecting or creating derived cache contents.
     *
     * @param entry cache entry to protect
     * @return caller-owned lease
     */
    @NotNull ArtifactLease lock(@NotNull Path entry);

    /**
     * Releases download transports after every user of this store has finished.
     */
    @Override
    void close();
}
