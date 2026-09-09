package me.whereareiam.anvil.protocol.api.channel;

/**
 * Owns a protocol event registration. Closing cancels subsequent and queued deliveries.
 */

public interface ProtocolSubscription extends AutoCloseable {
	/**
	 * Removes this registration without closing its player or transport.
	 */
	@Override
	void close();
}
