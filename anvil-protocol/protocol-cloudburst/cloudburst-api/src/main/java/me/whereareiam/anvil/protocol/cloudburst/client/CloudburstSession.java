package me.whereareiam.anvil.protocol.cloudburst.client;

import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One Bedrock connection as a {@link CloudburstClient} opens it: the native session that the worker hands to
 * capability bindings. A segment implements it over its release's connection, so feature segments that link against
 * the same release name the packet and item types through the type parameters, while the worker and host code never
 * see a CloudburstMC Protocol type.
 *
 * <p>Each session is one connection generation. A reconnect opens a new session: subscriptions belong to the session
 * they were made on and never receive a packet of another one, and once a session's connection closed it delivers
 * nothing more and refuses to send. Every received packet is dispatched from the session's single packet handler, on
 * the connection's network thread, to the subscriptions of its type and its supertypes in the order they were made,
 * before the client itself handles it.</p>
 *
 * <pre>{@code
 * CloudburstSession<BedrockPacket, ItemDefinition> session = packets.sessionType().cast(player.nativeSession());
 * PacketSubscription text = session.subscribe(TextPacket.class, packet -> received.accept(packet.getMessage()));
 * session.send(chat("Hello"));
 * }</pre>
 *
 * @param <P> packet base type of the release
 * @param <I> item definition type of the release
 */
public interface CloudburstSession<P, I> {
	/**
	 * Returns the Bedrock protocol number this session speaks, which selected its codec.
	 *
	 * @return native protocol number
	 */
	int protocol();

	/**
	 * Tests whether the session's connection is open.
	 *
	 * @return whether the session is connected
	 */
	boolean connected();

	/**
	 * Sends a packet on the session's connection.
	 *
	 * @param packet packet of the session's codec
	 * @throws IllegalStateException when the session is not connected
	 */
	void send(@NotNull P packet);

	/**
	 * Receives every packet of a type, including its subtypes, that this session's connection delivers from now on.
	 * A subscription made after the connection closed receives nothing.
	 *
	 * @param type packet class to receive
	 * @param listener receives each packet on the connection's network thread; a failing listener does not stop the
	 * packet from reaching the others or the client
	 * @param <T> packet type
	 * @return handle that ends the subscription
	 */
	<T extends P> @NotNull PacketSubscription subscribe(@NotNull Class<T> type, @NotNull Consumer<? super T> listener);

	/**
	 * Returns what the server's game start told the client, once it arrived.
	 *
	 * @return the game start, or empty before it arrived
	 */
	@NotNull Optional<GameStart> start();

	/**
	 * Returns the item definitions the server registered for this session: those of the game start for older
	 * protocols, and those of the item registry packet for newer ones, plus the empty item.
	 *
	 * @return immutable item definitions, empty before the server sent them
	 */
	@NotNull List<I> itemDefinitions();

	/**
	 * Returns the client tick an input sent now carries, such as that of a player authoritative input: the game
	 * start's tick, advanced by one every 50 milliseconds since the game start arrived.
	 *
	 * @return current client tick
	 * @throws IllegalStateException before the game start arrived
	 */
	long tick();
}
