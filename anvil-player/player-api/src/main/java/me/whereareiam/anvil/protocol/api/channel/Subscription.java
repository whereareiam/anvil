package me.whereareiam.anvil.protocol.api.channel;

/**
 * Owns a listener registration. Closing removes the listener and is idempotent.
 */
public interface Subscription extends AutoCloseable {
	/**
	 * Removes this registration without closing the event source.
	 */
	@Override
	void close();
}
