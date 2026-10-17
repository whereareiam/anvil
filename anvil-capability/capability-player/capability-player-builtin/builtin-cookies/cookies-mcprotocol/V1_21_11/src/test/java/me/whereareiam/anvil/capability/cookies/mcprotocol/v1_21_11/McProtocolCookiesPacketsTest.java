package me.whereareiam.anvil.capability.cookies.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.cookies.packet.CookiesPacketListener;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPackets;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundStoreCookiePacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.clientbound.ClientboundCookieRequestPacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.serverbound.ServerboundCookieResponsePacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class McProtocolCookiesPacketsTest {
	private static final byte[] TICKET = "ticket".getBytes(StandardCharsets.UTF_8);

	private final McProtocolCookiesPackets packets = new McProtocolCookiesPackets();

	@Test
	void reportsRequestsAndStoredCookiesWithTheirNamespace() {
		NativeSession session = new NativeSession();
		RecordingListener listener = new RecordingListener();
		packets.listen(session.proxy, listener);

		session.receive(new ClientboundStoreCookiePacket(Key.key("example:ticket"), TICKET));
		session.receive(new ClientboundCookieRequestPacket(Key.key("ticket")));
		session.receive(new ClientboundKeepAlivePacket(7));

		assertEquals(List.of("stored example:ticket", "requested minecraft:ticket"), listener.calls);
		assertArrayEquals(TICKET, listener.payloads.getFirst());
	}

	@Test
	void answersWithThePayloadOrWithNone() {
		NativeSession session = new NativeSession();

		packets.answer(session.proxy, "example:ticket", TICKET);
		packets.answer(session.proxy, "example:other", null);

		ServerboundCookieResponsePacket answered = assertInstanceOf(ServerboundCookieResponsePacket.class, session.sent.get(0));
		assertEquals(Key.key("example:ticket"), answered.getKey());
		assertArrayEquals(TICKET, answered.getPayload());
		ServerboundCookieResponsePacket none = assertInstanceOf(ServerboundCookieResponsePacket.class, session.sent.get(1));
		assertEquals(Key.key("example:other"), none.getKey());
		assertNull(none.getPayload());
	}

	@Test
	void reportsTheEndOfTheConnectionAndRemovesItsListenerWhenDetached() {
		NativeSession session = new NativeSession();
		RecordingListener listener = new RecordingListener();
		Runnable detach = packets.listen(session.proxy, listener);
		assertEquals(1, session.listeners.size());

		session.close();
		detach.run();

		assertEquals(List.of("closed"), listener.calls);
		assertEquals(List.of(), session.listeners);
	}

	@Test
	void isTheOnlyCookiesPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(CookiesPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolCookiesPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	private static final class RecordingListener implements CookiesPacketListener {
		private final List<String> calls = new ArrayList<>();
		private final List<byte[]> payloads = new ArrayList<>();

		@Override
		public void requested(@NotNull String key) {
			calls.add("requested " + key);
		}

		@Override
		public void stored(@NotNull String key, byte @NotNull [] payload) {
			calls.add("stored " + key);
			payloads.add(payload);
		}

		@Override
		public void closed() {
			calls.add("closed");
		}
	}

	/**
	 * An MCProtocolLib session that records sent packets and listeners and delivers received packets and its close
	 * synchronously.
	 */
	private static final class NativeSession {
		private final List<Packet> sent = new ArrayList<>();
		private final List<SessionListener> listeners = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(instance, method, arguments) -> handle(method, arguments));

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
		}

		private void close() {
			DisconnectedEvent event = new DisconnectedEvent(proxy, Component.text("Disconnected"), null);
			List.copyOf(listeners).forEach(listener -> listener.disconnected(event));
		}

		private Object handle(Method method, Object[] arguments) {
			switch (method.getName()) {
				case "send" -> sent.add((Packet) arguments[0]);
				case "addListener" -> listeners.add((SessionListener) arguments[0]);
				case "removeListener" -> listeners.remove((SessionListener) arguments[0]);
				default -> throw new UnsupportedOperationException(method.getName());
			}
			return null;
		}
	}
}
