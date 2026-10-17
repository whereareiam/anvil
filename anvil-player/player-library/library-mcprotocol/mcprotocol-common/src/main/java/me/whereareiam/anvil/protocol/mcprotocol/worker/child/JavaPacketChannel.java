package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One worker player's Java Edition protocol: it hands the packets of the player's current connection to their
 * listeners and sends packets on that connection, through the release's client segment.
 */
@RequiredArgsConstructor
final class JavaPacketChannel implements JavaProtocol {
	private final @NotNull McProtocolClient<Object> client;
	private final @NotNull Supplier<Object> currentSession;
	private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

	@Override
	public <P extends ClientboundPacket> @NotNull Subscription listen(
			@NotNull Class<P> type,
			@NotNull Consumer<? super P> listener
	) {
		requireSupported(type);
		Consumer<Object> typed = packet -> listener.accept(type.cast(packet));
		List<Consumer<Object>> subscribed = listeners.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>());
		subscribed.add(typed);

		return () -> subscribed.remove(typed);
	}

	@Override
	public void send(@NotNull ServerboundPacket packet) {
		requireSupported(packet.getClass());
		client.send(currentSession.get(), packet);
	}

	@Override
	public boolean supports(@NotNull Class<? extends JavaPacket> type) {
		return client.supports(type);
	}

	/**
	 * Hands a packet of the current connection to the listeners of its class. A failing listener is reported on
	 * standard error and does not keep the packet from the others.
	 *
	 * @param packet received packet
	 */
	void deliver(@NotNull ClientboundPacket packet) {
		List<Consumer<Object>> subscribed = listeners.get(packet.getClass());
		if (subscribed == null) return;

		for (Consumer<Object> listener : subscribed) {
			try {
				listener.accept(packet);
			} catch (RuntimeException failure) {
				System.err.println("[Anvil] Warning: a listener of " + packet.getClass().getSimpleName() + " failed: " + failure);
			}
		}
	}

	private void requireSupported(Class<? extends JavaPacket> type) {
		if (!client.supports(type))
			throw new ProtocolUnavailableException("the loaded " + McProtocolClient.LIBRARY_ID + " release does not carry " + type.getName());
	}
}
