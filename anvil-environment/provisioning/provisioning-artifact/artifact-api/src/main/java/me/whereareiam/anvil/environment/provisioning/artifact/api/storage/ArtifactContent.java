package me.whereareiam.anvil.environment.provisioning.artifact.api.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Prepares one complete artifact replacement, a downloaded file or an installed directory, before storage
 * publishes it.
 * @param <T> result derived from the prepared contents
 */

public interface ArtifactContent<T> {
	/**
	 * Writes complete contents into the supplied empty staging file or directory.
	 *
	 * @param staging unpublished file or directory owned by the storage transaction
	 * @return result needed by the acquisition operation
	 * @throws IOException when content cannot be prepared
	 */
	@Nullable T write(@NotNull Path staging) throws IOException;
}
