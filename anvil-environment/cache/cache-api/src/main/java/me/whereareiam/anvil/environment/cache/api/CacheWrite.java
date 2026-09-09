package me.whereareiam.anvil.environment.cache.api;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Owns temporary contents prepared for publication during an open cache entry operation.
 *
 * <p>Closing an uncommitted write discards staging. Commit publishes the complete prepared payload;
 * replacement and rollback guarantees depend on whether the entry requested a file write or a
 * file/directory replacement. The API does not promise recovery from process crashes.</p>
 */
public interface CacheWrite extends AutoCloseable {
	/**
	 * Returns the staging location where the caller prepares complete contents.
	 *
	 * @return temporary path owned by this write
	 */
	@NotNull Path path();

	/**
	 * Publishes the prepared contents while the source entry remains exclusively open.
	 *
	 * @throws IOException when publication or rollback fails
	 */
	void commit() throws IOException;

	/**
	 * Removes unpublished staging and any obsolete backup after successful publication.
	 *
	 * <p>A backup that could not be restored during rollback is retained for diagnosis. Repeated
	 * close calls have no additional effect. The owning entry also closes this write if necessary.</p>
	 *
	 * @throws IOException when temporary contents or an obsolete backup cannot be removed
	 */
	@Override
	void close() throws IOException;
}
