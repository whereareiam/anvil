package me.whereareiam.anvil.protocol.java.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

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
 * {@link JavaWorld}, which the library offers its extensions beside the protocol.
 *
 * <pre>{@code
 * JavaPacketDispatcher protocol = new JavaPacketDispatcher(LIBRARY_ID, connection);
 * WorkerProtocols protocols = WorkerProtocols.builder(LIBRARY_ID)
 *         .speak(JavaProtocol.class, protocol, protocol::scoped)
 *         .speak(JavaWorld.class, protocol.world(), track -> protocol.world())
 *         .build();
 * ...
 * protocol.deliver(packet); // for each packet the player's connection receives
 * protocol.disconnected(); // once the player's connection closed
 * }</pre>
 */
@RequiredArgsConstructor
public final class JavaPacketDispatcher implements JavaProtocol {
	private final @NotNull String libraryId;
	private final @NotNull JavaConnection connection;
	private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
	private final Map<Class<?>, List<Consumer<Object>>> sentListeners = new ConcurrentHashMap<>();
	private final JavaChunkStore world = new JavaChunkStore();

	@Override
	public <P extends ClientboundPacket> @NotNull Subscription listen(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		return subscribe(listeners, type, listener);
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

	/**
	 * Hands a packet of the player's current connection to the listeners of its class, once the player's world
	 * applied it. A failing listener is reported on standard error and does not keep the packet from the others.
	 *
	 * @param packet received packet
	 */
	public void deliver(@NotNull ClientboundPacket packet) {
		world.receive(packet);
		handOut(listeners, packet);
	}

	/**
	 * Tells that the player's current connection closed, so the player's world forgets its level and its chunks.
	 */
	public void disconnected() {
		world.clear();
	}

	/**
	 * Returns the world the player's client knows, which the packets delivered to this protocol keep up to date.
	 *
	 * @return the player's world, the same for every connection
	 */
	public @NotNull JavaWorld world() {
		return world;
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
	}
}
