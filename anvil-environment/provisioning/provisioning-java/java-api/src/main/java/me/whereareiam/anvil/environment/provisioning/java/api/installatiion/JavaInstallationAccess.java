package me.whereareiam.anvil.environment.provisioning.java.api.installatiion;

import java.io.IOException;

/**
 * Owns exclusive access to one Java installation until inspection or preparation completes.
 */

public interface JavaInstallationAccess extends AutoCloseable {
	/**
	 * Releases ownership after installation operations finish.
	 * @throws IOException when ownership cannot be released
	 */
	@Override
	void close() throws IOException;
}
