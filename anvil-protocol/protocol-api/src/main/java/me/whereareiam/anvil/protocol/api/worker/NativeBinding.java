package me.whereareiam.anvil.protocol.api.worker;

/**
 * Owns one native player's extension state and listener registrations.
 */

public interface NativeBinding extends AutoCloseable {
	/**
	 * Releases this binding after the player stops accepting operations.
	 */
	@Override
	void close();
}
