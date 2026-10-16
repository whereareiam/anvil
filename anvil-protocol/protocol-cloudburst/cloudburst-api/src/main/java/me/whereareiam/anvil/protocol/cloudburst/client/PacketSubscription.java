package me.whereareiam.anvil.protocol.cloudburst.client;

/**
 * A subscription to the packets of one {@link CloudburstSession}, ended by closing it.
 *
 * <pre>{@code
 * try (PacketSubscription subscription = session.subscribe(TextPacket.class, received::add)) {
 *     session.send(command);
 * }
 * }</pre>
 */
public interface PacketSubscription extends AutoCloseable {
	/**
	 * Stops delivering packets to the subscription's listener; closing it again has no effect.
	 */
	@Override
	void close();
}
