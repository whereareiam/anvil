package me.whereareiam.anvil.edition.java.protocol.worker;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.data.packet.JavaPacketData;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.LocalPlayer;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundFrame;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPackets;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.type.connection.ConnectionState;
import me.whereareiam.anvil.edition.java.protocol.worker.answer.ClientAnswers;
import me.whereareiam.anvil.edition.java.protocol.worker.position.ClientPosition;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * One worker player's Java Edition protocol over a library's {@link JavaConnection}: it hands delivered packets and
 * sent ones to their listeners; the connection refuses a packet its release cannot send, and this protocol a listener
 * of a packet the release never carries. Each delivered packet first updates the {@link JavaWorld} and the client's
 * {@link #player() own player} and gets its {@link ClientAnswers}, under the client's lock, which listeners run under
 * too, so a library delivers packets in arrival order and answers only what vanilla answers on its network thread,
 * such as a keep-alive. The library {@link #start() starts} the ticks once the extensions are bound.
 *
 * <p>A library asks {@link #wants(Class)} before it decodes a costly packet, such as a chunk column, and skips it while
 * nothing wants it. While a frame listener exists, {@link #wantsFrames()} tells a library that hands over frames to
 * deliver each packet as its frame, and every packet but the {@linkplain ClientboundPackets#isCostly costly} ones is
 * wanted; a costly packet's frame carries its value only while something wants it.</p>
 *
 * <pre>{@code
 * JavaPacketDispatcher protocol = new JavaPacketDispatcher(LIBRARY_ID, protocolNumber, version, name, connection);
 * protocol.start(); // once the player's extensions are bound
 * if (protocol.wantsFrames()) protocol.deliver(state, packetId, payload, translated);
 * else if (translated != null) protocol.deliver(translated); // for each packet the player's connection receives
 * protocol.disconnected(); // once the player's connection closed
 * protocol.close(); // once the player is closed
 * }</pre>
 */
public final class JavaPacketDispatcher implements JavaProtocol, AutoCloseable {
	private final @NotNull String libraryId;
	private final int protocolNumber;
	private final @NotNull JavaConnection connection;
	private final PacketListeners listeners;
	private final JavaWorldStore world = new JavaWorldStore();
	private final ClientPosition player;
	private final ClientAnswers answers;

	/**
	 * Creates the protocol of one player, whose client does not tick until {@link #start() started}.
	 *
	 * @param libraryId the ID of the player's protocol library, which refusals name
	 * @param protocolNumber the number of the protocol the player's release speaks
	 * @param clientVersion the Minecraft version of the player's client
	 * @param playerName the player's name, which names its client's thread
	 * @param connection what the library supplies
	 */
	public JavaPacketDispatcher(
			@NotNull String libraryId,
			int protocolNumber,
			@NotNull MinecraftVersion clientVersion,
			@NotNull String playerName,
			@NotNull JavaConnection connection
	) {
		this.libraryId = libraryId;
		this.protocolNumber = protocolNumber;
		this.connection = connection;
		this.listeners = new PacketListeners(JavaPacketData.shipped().find(protocolNumber).orElse(null));
		this.player = new ClientPosition(clientVersion, playerName, this, world);
		this.answers = new ClientAnswers(this, clientVersion);
		if (player.readsChunks()) world.storeChunks();
	}

	@Override
	public <P extends ClientboundPacket> @NotNull Subscription listen(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		return listeners.received(carried(type), listener);
	}

	@Override
	public boolean handsOverFrames() {
		return connection.handsOverFrames();
	}

	@Override
	public @NotNull Subscription listenFrames(@NotNull Consumer<? super ClientboundFrame> listener) {
		if (!connection.handsOverFrames())
			throw new ProtocolUnavailableException("the " + libraryId + " library does not hand over the frames its "
					+ "players receive");

		return listeners.frames(listener);
	}

	@Override
	public @NotNull Subscription want(@NotNull Class<? extends ClientboundPacket> type) {
		return listeners.want(carried(type));
	}

	@Override
	public void send(@NotNull ServerboundPacket packet) {
		connection.send(packet);
		listeners.handOutSent(packet);
	}

	@Override
	public <P extends ServerboundPacket> @NotNull Subscription listenSent(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		return listeners.sent(carried(type), listener);
	}

	@Override
	public boolean supports(@NotNull Class<? extends JavaPacket> type) {
		return connection.supports(type);
	}

	@Override
	public int protocolNumber() {
		return protocolNumber;
	}

	@Override
	public @NotNull JavaWorld world() {
		world.storeChunks();
		return world;
	}

	@Override
	public @NotNull LocalPlayer player() {
		return player;
	}

	/**
	 * Starts the ticks of the player's client, 20 a second, unless they run.
	 *
	 * @throws IllegalStateException when the protocol is closed
	 */
	public void start() {
		player.start();
	}

	/**
	 * Stops the ticks of the player's client for good, as the player closes.
	 */
	@Override
	public void close() {
		player.close();
	}

	/**
	 * Tells whether anything wants the packets of a class: a listener of the class, {@link #want(Class)}, the world once
	 * it keeps chunks, or a frame listener, which wants every packet but the {@linkplain ClientboundPackets#isCostly
	 * costly} ones.
	 *
	 * @param type packet class
	 * @return whether a packet of the class should be decoded and delivered
	 */
	public boolean wants(@NotNull Class<? extends ClientboundPacket> type) {
		return world.wants(type) || listeners.wants(type) || wantsFrames() && !ClientboundPackets.isCostly(type);
	}

	/**
	 * Hands a packet of the player's current connection to the listeners of its class, once the player's world and
	 * client applied and answered it, past a failing listener, which is reported on standard error.
	 *
	 * @param packet received packet
	 */
	public void deliver(@NotNull ClientboundPacket packet) {
		player.run(() -> {
			world.apply(packet);
			player.apply(packet);
			answers.apply(packet);
			listeners.handOutReceived(packet);
		});
	}

	/**
	 * Tells whether a frame listener exists, so a library that hands over frames delivers each packet it receives
	 * through {@link #deliver(ConnectionState, int, byte[], ClientboundPacket)}.
	 */
	public boolean wantsFrames() {
		return listeners.wantsFrames();
	}

	/**
	 * Hands a packet of the player's current connection over as its frame: its value, when modelled, as
	 * {@link #deliver(ClientboundPacket)} does, then the frame, named by the protocol, to the frame listeners, past a
	 * failing one.
	 *
	 * @param state the connection's state when the packet arrived
	 * @param id the packet's ID on the wire
	 * @param payload the packet's bytes after its ID, decrypted and decompressed
	 * @param packet the packet's value, or {@code null} when the release does not translate the packet
	 */
	public void deliver(
			@NotNull ConnectionState state,
			int id,
			byte @NotNull [] payload,
			@Nullable ClientboundPacket packet
	) {
		player.run(() -> {
			if (packet != null) deliver(packet);
			listeners.handOut(state, id, payload, packet);
		});
	}

	/**
	 * Tells that the player's current connection closed: the world forgets its level and chunks, the client its player.
	 */
	public void disconnected() {
		player.run(() -> {
			world.clear();
			player.disconnected();
		});
	}

	/**
	 * Returns a view of this protocol that reports each subscription it creates, for the worker to release the
	 * listeners of an extension whose binding failed.
	 *
	 * @param track receives every subscription the view creates
	 * @return the scoped view
	 */
	public @NotNull JavaProtocol scoped(@NotNull Consumer<Subscription> track) {
		return new ScopedJavaProtocol(this, player.scoped(track), track);
	}

	/**
	 * Returns the client's own player with what only a test that drives the client itself uses: its ticks one at a time
	 * and a placement without the server's handshake.
	 */
	@NotNull ClientPosition client() {
		return player;
	}

	private <P extends JavaPacket> @NotNull Class<P> carried(@NotNull Class<P> type) {
		if (!connection.supports(type))
			throw new ProtocolUnavailableException("the loaded " + libraryId + " release does not carry "
					+ type.getName());

		return type;
	}
}
