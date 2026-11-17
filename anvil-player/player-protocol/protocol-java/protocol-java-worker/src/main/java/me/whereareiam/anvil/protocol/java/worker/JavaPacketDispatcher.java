package me.whereareiam.anvil.protocol.java.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundFrame;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPackets;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.java.packet.JavaPacketData;
import me.whereareiam.anvil.protocol.java.packet.PacketRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * One worker player's Java Edition protocol over a library's {@link JavaConnection}: it hands the packets the library
 * delivers to their listeners, sends packets through the connection and then hands each sent packet to the listeners
 * of sent packets. The connection refuses a packet its release cannot send; a listener of a packet the release never
 * carries is refused here, since no delivered packet would tell. Every delivered packet first updates the player's
 * {@link JavaWorld}, which {@link #world()} hands out.
 *
 * <p>A library asks {@link #wants(Class)} before it decodes a packet that is costly to decode, such as a chunk column,
 * and skips it while nothing wants it. While a frame listener exists, {@link #wantsFrames()} tells a library that hands
 * over frames to deliver each packet it receives as its frame, which the dispatcher names by the player's protocol from
 * Anvil's packet data, and every packet the protocol models is wanted except the
 * {@linkplain ClientboundPackets#isCostly costly} ones, which only a listener of their class, {@link #want(Class)} or
 * the player's world wants, so only then does their frame carry their value.</p>
 *
 * <pre>{@code
 * JavaPacketDispatcher protocol = new JavaPacketDispatcher(LIBRARY_ID, release.protocolNumber(), connection);
 * ...
 * if (protocol.wantsFrames()) protocol.deliver(state, packetId, payload, translated);
 * else if (translated != null) protocol.deliver(translated); // for each packet the player's connection receives
 * protocol.disconnected(); // once the player's connection closed
 * }</pre>
 */
public final class JavaPacketDispatcher implements JavaProtocol {
	private final @NotNull String libraryId;
	private final int protocolNumber;
	private final @NotNull JavaConnection connection;
	private final @Nullable PacketRegistry names;
	private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
	private final Map<Class<?>, List<Consumer<Object>>> sentListeners = new ConcurrentHashMap<>();
	private final List<Consumer<? super ClientboundFrame>> frameListeners = new CopyOnWriteArrayList<>();
	private final Map<Class<?>, List<Want>> wanted = new ConcurrentHashMap<>();
	private final JavaWorldStore world = new JavaWorldStore();

	/**
	 * Creates the protocol of one player.
	 *
	 * @param libraryId the ID of the player's protocol library, which refusals name
	 * @param protocolNumber the number of the protocol the player's release speaks
	 * @param connection what the library supplies
	 */
	public JavaPacketDispatcher(@NotNull String libraryId, int protocolNumber, @NotNull JavaConnection connection) {
		this.libraryId = libraryId;
		this.protocolNumber = protocolNumber;
		this.connection = connection;
		this.names = JavaPacketData.shipped().find(protocolNumber).orElse(null);
	}

	@Override
	public <P extends ClientboundPacket> @NotNull Subscription listen(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		return subscribe(listeners, type, listener);
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

		frameListeners.add(listener);
		return () -> frameListeners.remove(listener);
	}

	@Override
	public @NotNull Subscription want(@NotNull Class<? extends ClientboundPacket> type) {
		if (!connection.supports(type))
			throw new ProtocolUnavailableException("the loaded " + libraryId + " release does not carry "
					+ type.getName());

		Want want = new Want(wanted.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()));
		want.wants.add(want);

		return want;
	}

	@Override
	public void send(@NotNull ServerboundPacket packet) {
		connection.send(packet);
		handOut(sentListeners, packet);
	}

	@Override
	public <P extends ServerboundPacket> @NotNull Subscription listenSent(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		return subscribe(sentListeners, type, listener);
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

	/**
	 * Tells whether anything wants the packets of a class: a listener of the class, {@link #want(Class)}, the player's
	 * world, which wants the packets of its chunks once something asked for it, or a frame listener, which wants the
	 * value of every packet that is not {@linkplain ClientboundPackets#isCostly costly}.
	 *
	 * @param type packet class
	 * @return whether a packet of the class should be decoded and delivered
	 */
	public boolean wants(@NotNull Class<? extends ClientboundPacket> type) {
		if (world.wants(type) || present(listeners.get(type)) || present(wanted.get(type))) return true;

		return wantsFrames() && !ClientboundPackets.isCostly(type);
	}

	private static boolean present(@Nullable List<?> entries) {
		return entries != null && !entries.isEmpty();
	}

	/**
	 * Hands a packet of the player's current connection to the listeners of its class, once the player's world
	 * applied it. A failing listener is reported on standard error and does not keep the packet from the others.
	 *
	 * @param packet received packet
	 */
	public void deliver(@NotNull ClientboundPacket packet) {
		world.apply(packet);
		handOut(listeners, packet);
	}

	/**
	 * Tells whether a frame listener exists, so a library that hands over frames delivers each packet it receives
	 * through {@link #deliver(ConnectionState, int, byte[], ClientboundPacket)}.
	 *
	 * @return whether frames are wanted
	 */
	public boolean wantsFrames() {
		return !frameListeners.isEmpty();
	}

	/**
	 * Hands a packet of the player's current connection over as its frame: its value, when the protocol models the
	 * packet, to the player's world and the listeners of its class as {@link #deliver(ClientboundPacket)} does, and
	 * then the frame, named by the player's protocol, to the frame listeners. A failing listener is reported on standard
	 * error and does not keep the frame from the others.
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
		if (packet != null) deliver(packet);
		if (frameListeners.isEmpty()) return;

		String name = names == null ? null : names.clientbound(state, id).orElse(null);
		ClientboundFrame frame = new ClientboundFrame(state, id, name, payload, packet);
		for (Consumer<? super ClientboundFrame> listener : frameListeners) {
			try {
				listener.accept(frame);
			} catch (RuntimeException failure) {
				System.err.println("[Anvil] Warning: a frame listener failed: " + failure);
			}
		}
	}

	/**
	 * Tells that the player's current connection closed, so the player's world forgets its level and its chunks.
	 */
	public void disconnected() {
		world.clear();
	}

	/**
	 * Returns a view of this protocol that reports each subscription it creates, for the worker to release the
	 * listeners of an extension whose binding failed.
	 *
	 * @param track receives every subscription the view creates
	 * @return the scoped view
	 */
	public @NotNull JavaProtocol scoped(@NotNull Consumer<Subscription> track) {
		return new ScopedView(track);
	}

	private <P extends JavaPacket> Subscription subscribe(
			Map<Class<?>, List<Consumer<Object>>> table,
			Class<P> type,
			Consumer<? super P> listener
	) {
		if (!connection.supports(type))
			throw new ProtocolUnavailableException("the loaded " + libraryId + " release does not carry "
					+ type.getName());

		Consumer<Object> typed = packet -> listener.accept(type.cast(packet));
		List<Consumer<Object>> subscribed = table.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>());
		subscribed.add(typed);

		return () -> subscribed.remove(typed);
	}

	/**
	 * Hands a packet to the listeners of its class in one table. A failing listener is reported on standard error
	 * and does not keep the packet from the others.
	 */
	private static void handOut(Map<Class<?>, List<Consumer<Object>>> table, JavaPacket packet) {
		List<Consumer<Object>> subscribed = table.get(packet.getClass());
		if (subscribed == null) return;

		for (Consumer<Object> listener : subscribed) {
			try {
				listener.accept(packet);
			} catch (RuntimeException failure) {
				System.err.println("[Anvil] Warning: a listener of " + packet.getClass().getSimpleName() + " failed: "
						+ failure);
			}
		}
	}

	/**
	 * One open {@link #want(Class)}, which wants its class while it is listed among the class's wants.
	 */
	@RequiredArgsConstructor
	private static final class Want implements Subscription {
		private final List<Want> wants;

		@Override
		public void close() {
			wants.remove(this);
		}
	}

	/**
	 * The protocol as one extension sees it while binding: the subscriptions it creates are reported to its scope.
	 */
	@RequiredArgsConstructor
	private final class ScopedView implements JavaProtocol {
		private final Consumer<Subscription> track;

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			Subscription subscription = JavaPacketDispatcher.this.listen(type, listener);
			track.accept(subscription);

			return subscription;
		}

		@Override
		public @NotNull Subscription listenFrames(@NotNull Consumer<? super ClientboundFrame> listener) {
			Subscription subscription = JavaPacketDispatcher.this.listenFrames(listener);
			track.accept(subscription);

			return subscription;
		}

		@Override
		public @NotNull Subscription want(@NotNull Class<? extends ClientboundPacket> type) {
			Subscription subscription = JavaPacketDispatcher.this.want(type);
			track.accept(subscription);

			return subscription;
		}

		@Override
		public boolean handsOverFrames() {
			return JavaPacketDispatcher.this.handsOverFrames();
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			JavaPacketDispatcher.this.send(packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			Subscription subscription = JavaPacketDispatcher.this.listenSent(type, listener);
			track.accept(subscription);

			return subscription;
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return JavaPacketDispatcher.this.supports(type);
		}

		@Override
		public int protocolNumber() {
			return protocolNumber;
		}

		@Override
		public @NotNull JavaWorld world() {
			return JavaPacketDispatcher.this.world();
		}
	}
}
