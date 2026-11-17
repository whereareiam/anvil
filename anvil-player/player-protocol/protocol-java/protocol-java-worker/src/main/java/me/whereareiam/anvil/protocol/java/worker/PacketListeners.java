package me.whereareiam.anvil.protocol.java.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundFrame;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.connection.ConnectionState;
import me.whereareiam.anvil.protocol.java.packet.PacketRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Who listens to one player's packets: the listeners of the packets it receives and sends, by class, the listeners of
 * its frames, which it names by the player's protocol, and the open wants of a class. Each hand-out reports a failing listener on standard error and goes on with
 * the others. Its {@link JavaPacketDispatcher} decides what the release carries before it subscribes anything here.
 */
@RequiredArgsConstructor
final class PacketListeners {
	/**
	 * The packet names of the player's protocol, which name its frames, or {@code null} for a protocol Anvil ships no
	 * packet data of.
	 */
	private final @Nullable PacketRegistry names;
	private final Map<Class<?>, List<Consumer<Object>>> received = new ConcurrentHashMap<>();
	private final Map<Class<?>, List<Consumer<Object>>> sent = new ConcurrentHashMap<>();
	private final List<Consumer<? super ClientboundFrame>> frames = new CopyOnWriteArrayList<>();
	private final Map<Class<?>, List<Want>> wanted = new ConcurrentHashMap<>();

	<P extends ClientboundPacket> @NotNull Subscription received(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		return subscribe(received, type, listener);
	}

	<P extends ServerboundPacket> @NotNull Subscription sent(@NotNull Class<P> type, @NotNull Consumer<? super P> listener) {
		return subscribe(sent, type, listener);
	}

	@NotNull Subscription frames(@NotNull Consumer<? super ClientboundFrame> listener) {
		frames.add(listener);
		return () -> frames.remove(listener);
	}

	@NotNull Subscription want(@NotNull Class<? extends ClientboundPacket> type) {
		Want want = new Want(wanted.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()));
		want.wants.add(want);
		return want;
	}

	/**
	 * Tells whether a listener of a class or a want of it is open.
	 */
	boolean wants(@NotNull Class<? extends ClientboundPacket> type) {
		return present(received.get(type)) || present(wanted.get(type));
	}

	boolean wantsFrames() {
		return !frames.isEmpty();
	}

	void handOutReceived(@NotNull ClientboundPacket packet) {
		handOut(received, packet);
	}

	void handOutSent(@NotNull ServerboundPacket packet) {
		handOut(sent, packet);
	}

	/**
	 * Hands a received packet over as its frame, named by the player's protocol, while a frame listener exists.
	 */
	void handOut(@NotNull ConnectionState state, int id, byte @NotNull [] payload, @Nullable ClientboundPacket packet) {
		if (frames.isEmpty()) return;

		String name = names == null ? null : names.clientbound(state, id).orElse(null);
		ClientboundFrame frame = new ClientboundFrame(state, id, name, payload, packet);
		for (Consumer<? super ClientboundFrame> listener : frames) {
			try {
				listener.accept(frame);
			} catch (RuntimeException failure) {
				System.err.println("[Anvil] Warning: a frame listener failed: " + failure);
			}
		}
	}

	private static <P extends JavaPacket> Subscription subscribe(
			Map<Class<?>, List<Consumer<Object>>> table,
			Class<P> type,
			Consumer<? super P> listener
	) {
		Consumer<Object> typed = packet -> listener.accept(type.cast(packet));
		List<Consumer<Object>> subscribed = table.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>());
		subscribed.add(typed);

		return () -> subscribed.remove(typed);
	}

	/**
	 * Hands a packet to the listeners of its class in one table.
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

	private static boolean present(@Nullable List<?> entries) {
		return entries != null && !entries.isEmpty();
	}

	/**
	 * One open want, which wants its class while it is listed among the class's wants.
	 */
	@RequiredArgsConstructor
	private static final class Want implements Subscription {
		private final List<Want> wants;

		@Override
		public void close() {
			wants.remove(this);
		}
	}
}
