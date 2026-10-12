package me.whereareiam.anvil.protocol.cloudburst.client;

import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
	 * Returns the profile the session's login claimed. The client names itself by it where a packet carries its own
	 * name, such as the source of a chat message it sends.
	 *
	 * @return claimed profile
	 */
	@NotNull ClientProfile profile();

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
	 * Tests whether the server is authoritative over the player's movement, in which case the client reports its
	 * position and input in player authoritative input packets instead of moving by player move packets. Protocols
	 * from 818 on know only server authority; older protocols take it from the game start, and a movement authority
	 * packet the server sends later replaces it.
	 *
	 * @return whether the client reports movement as authoritative input
	 * @throws IllegalStateException before the game start arrived
	 */
	boolean serverAuthoritativeMovement();

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

	/**
	 * Returns the runtime id of the block at a position of the dimension the player is in, as the server's chunks and
	 * block updates last told the client. The id is the one the server sends on the wire: a palette index, or the
	 * block state hash when the game start says block network ids are hashed. It is the block of the first layer,
	 * the one an interaction targets; the waterlogging layer is not tracked.
	 *
	 * <p>The client keeps the chunks the server sent until the server unloads them for it, moves it to another
	 * dimension or its chunk publisher no longer covers them. A block is unknown when its chunk was never received or
	 * was dropped, when it lies outside the dimension's height, or when the server sent its sub-chunk without block
	 * storage, as it does for sub-chunks that hold only air: the client knows no block names, so it cannot tell
	 * which runtime id air has.</p>
	 *
	 * <pre>{@code
	 * // transaction is the InventoryTransactionPacket of a click on that block
	 * OptionalInt block = session.blockAt(10, 64, -3);
	 * block.ifPresent(runtimeId -> transaction.setBlockDefinition(() -> runtimeId));
	 * }</pre>
	 *
	 * @param x block x coordinate
	 * @param y block y coordinate
	 * @param z block z coordinate
	 * @return the block's runtime id, or empty when the client does not know the block
	 */
	@NotNull OptionalInt blockAt(int x, int y, int z);
}
