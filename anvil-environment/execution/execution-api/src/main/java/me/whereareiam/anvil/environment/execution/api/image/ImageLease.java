package me.whereareiam.anvil.environment.execution.api.image;

import java.io.IOException;

/**
 * Owns exclusive access while an execution provider reads and updates image metadata.
 */

public interface ImageLease extends AutoCloseable {
	/**
	 * Releases image coordination after every metadata operation has completed.
	 *
	 * @throws IOException when the lease cannot be released
	 */
	@Override
	void close() throws IOException;
}
