package me.whereareiam.anvil.environment.provisioning.artifact.api.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Owns exclusive access to the current file and any replacement being downloaded.
 */
public interface ArtifactEntry extends AutoCloseable {
	/**
	 * Returns the normalized current file location, which may not exist yet.
	 * @return exclusively held destination
	 */
	@NotNull Path path();

	/**
	 * Prepares and publishes a complete replacement while retaining exclusive access.
	 * Failed content preparation preserves the previous destination and removes staging.
	 *
	 * @param content downloads and verifies the complete replacement in the supplied staging file
	 * @param <T> value produced while preparing content
	 * @return content result after successful publication
	 * @throws IOException when staging, content preparation, or publication fails
	 */
	@Nullable <T> T replace(@NotNull ArtifactContent<T> content) throws IOException;

	/**
	 * Releases exclusive access after every replacement has completed or rolled back.
	 * @throws IOException when storage cleanup fails
	 */
	@Override
	void close() throws IOException;
}
