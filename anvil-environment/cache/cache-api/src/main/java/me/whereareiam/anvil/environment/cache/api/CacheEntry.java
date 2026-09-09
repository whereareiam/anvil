package me.whereareiam.anvil.environment.cache.api;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Owns exclusive access to one cache entry and any staged writes created through it.
 *
 * <p>Use the entry and its writes on the thread that opened it. Closing the entry closes outstanding
 * writes before releasing access, including when cleanup fails. A caller may close a write earlier
 * with try-with-resources to report its cleanup failure alongside the operation that failed.</p>
 */
public interface CacheEntry extends AutoCloseable {
	/**
	 * Returns the protected entry location.
	 *
	 * @return normalized entry path; exclusive access lasts only while this entry is open
	 */
	@NotNull Path path();

	/**
	 * Creates an empty staging file for replacing this entry as one complete file.
	 *
	 * @return entry-owned staged write, also suitable for an inner try-with-resources scope
	 * @throws IOException when its parent or temporary file cannot be prepared
	 */
	@NotNull CacheWrite stageFile() throws IOException;

	/**
	 * Reserves a staging path for replacing this entry with a file or directory.
	 *
	 * <p>The staging path does not exist yet; the caller prepares its complete contents before
	 * committing. Publication can replace an existing file or nonempty directory.</p>
	 *
	 * @return entry-owned staged write with an uncreated payload path
	 * @throws IOException when the destination parent cannot be prepared
	 */
	@NotNull CacheWrite stageReplacement() throws IOException;

	/**
	 * Reserves a staging path for restoring this entry's contents to another destination.
	 *
	 * <p>The source entry remains protected while the caller copies and publishes the restore.
	 * This does not acquire separate access to the destination; the caller must validate and own
	 * that destination, such as an exclusively held process workspace.</p>
	 *
	 * @param destination validated file or directory to replace
	 * @return entry-owned staged write with an uncreated payload path
	 * @throws IOException when the destination parent cannot be prepared
	 */
	@NotNull CacheWrite stageReplacement(@NotNull Path destination) throws IOException;

	/**
	 * Closes outstanding writes before releasing exclusive entry access.
	 *
	 * <p>Every cleanup is attempted. Cleanup failures are reported as a cache exception with
	 * secondary failures suppressed. Repeated close calls have no additional effect.</p>
	 */
	@Override
	void close();
}
