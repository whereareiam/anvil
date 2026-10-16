package me.whereareiam.anvil.protocol.cloudburst.client;

import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientPosition;
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
 * the same release name the packet, item definition and item stack types through the type parameters, while the
 * worker and host code never see a CloudburstMC Protocol type.
 *
 * <p>Like a real client, the session keeps what it knows about its own player: the item stacks of the player's
 * containers and of containers the server opened, the selected hotbar slot and the position. It follows the server's
 * packets about them and the packets it sends itself, so a packet a feature segment sends through {@link #send}
 * updates them as the client's own prediction would; a server's rejection or correction replaces that prediction.</p>
 *
 * <p>Each session is one connection generation. A reconnect opens a new session with nothing known about its player:
 * subscriptions belong to the session they were made on and never receive a packet of another one, and once a
 * session's connection closed it delivers nothing more and refuses to send. Every received packet is dispatched from
 * the session's single packet handler, on the connection's network thread, to the subscriptions of its type and its
 * supertypes in the order they were made, after the session's knowledge of its player followed it and before the
 * client itself answers it.</p>
 *
 * <pre>{@code
 * CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session = packets.sessionType().cast(player.nativeSession());
 * PacketSubscription text = session.subscribe(TextPacket.class, packet -> received.accept(packet.getMessage()));
 * session.send(chat("Hello"));
 * }</pre>
 *
 * @param <P> packet base type of the release
 * @param <I> item definition type of the release
 * @param <T> item stack type of the release
 */
public interface CloudburstSession<P, I, T> {
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
	 * @param <Q> packet type
	 * @return handle that ends the subscription
	 */
	<Q extends P> @NotNull PacketSubscription subscribe(@NotNull Class<Q> type, @NotNull Consumer<? super Q> listener);

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
	 * Tests whether the server is authoritative over the player's inventory, in which case the client moves items
	 * with item stack requests that the server answers with item stack responses; otherwise it reports each move as
	 * an inventory transaction of the slots it changed. The game start names it.
	 *
	 * @return whether the client moves items with item stack requests
	 * @throws IllegalStateException before the game start arrived
	 */
	boolean serverAuthoritativeInventory();

	/**
	 * Takes the ID of the client's next item stack request: the first request of a session is {@code -1}, and each
	 * later one two less, so that the IDs of item stack requests stay negative and odd.
	 *
	 * @return the ID a new item stack request carries
	 */
	int nextItemStackRequestId();

	/**
	 * Returns the hotbar slot the player holds: the one the server selected last or the client selected itself
	 * through {@link #select(int)} or a mob equipment packet of its own, whichever came last.
	 *
	 * @return selected hotbar slot from zero through eight
	 */
	int selectedSlot();

	/**
	 * Returns the item stack the client believes a container slot holds. The client follows the player's inventory
	 * (container {@code 0}, hotbar {@code 0..8} and main inventory {@code 9..35}), off hand (container {@code 119}),
	 * armor (container {@code 120}) and user interface (container {@code 124}, whose slot {@code 0} is the cursor)
	 * for the whole session, and each container the server opens until it is closed. Items it moved itself are
	 * predicted until the server answers; a stack the server has not yet named carries the request ID as its
	 * network ID, as a real client's prediction does.
	 *
	 * @param containerId Bedrock container ID
	 * @param slot slot within the container
	 * @return the slot's item stack, or empty when the slot is empty or the client does not know the container
	 */
	@NotNull Optional<T> item(int containerId, int slot);

	/**
	 * Selects a hotbar slot as a client does: sends a mob equipment packet of the player's own entity that carries
	 * the item the slot holds, or the empty item, and records the slot as selected.
	 *
	 * @param slot hotbar slot from zero through eight
	 * @throws IllegalArgumentException when the slot is outside the hotbar
	 * @throws IllegalStateException before the game start arrived or when the session is not connected
	 */
	void select(int slot);

	/**
	 * Returns where the client believes its player is: the game start's position, replaced by each position the
	 * client sends in a player move or player authoritative input and by each position the server sets with a player
	 * move of the player's own entity or a movement prediction correction.
	 *
	 * @return the player's position at its eyes
	 * @throws IllegalStateException before the game start arrived
	 */
	@NotNull ClientPosition position();

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
