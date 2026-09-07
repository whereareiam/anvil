package me.whereareiam.anvil.provisioning.api.artifact;

/**
 * Exclusive ownership of a cache entry across threads and Anvil processes.
 */
public interface ArtifactLease extends AutoCloseable {
    /**
     * Releases the entry; closing is idempotent.
     */
    @Override
    void close();
}
