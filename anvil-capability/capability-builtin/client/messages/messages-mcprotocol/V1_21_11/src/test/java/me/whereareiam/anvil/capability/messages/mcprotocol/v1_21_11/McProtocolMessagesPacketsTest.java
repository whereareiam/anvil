package me.whereareiam.anvil.capability.messages.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatFilterType;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolMessagesPacketsTest {
	private final McProtocolMessagesPackets packets = new McProtocolMessagesPackets();

	@Test
	void sendsChatUnsignedWithNothingAcknowledgedAndAnUnverifiedChecksum() {
		NativeSession session = new NativeSession();
		long before = Instant.now().toEpochMilli();

		packets.chat(session.proxy, "hello there");

		assertEquals(1, session.sent.size());
		ServerboundChatPacket packet = assertInstanceOf(ServerboundChatPacket.class, session.sent.getFirst());
		assertEquals("hello there", packet.getMessage());
		assertTrue(packet.getTimeStamp() >= before, "timestamp " + packet.getTimeStamp());
		assertEquals(0L, packet.getSalt());
		assertNull(packet.getSignature());
		assertEquals(0, packet.getOffset());
		assertEquals(new BitSet(20), packet.getAcknowledgedMessages());
		assertEquals(0, packet.getChecksum());
	}

	@Test
	void sendsCommandsThroughTheUnsignedCommandPacket() {
		NativeSession session = new NativeSession();

		packets.command(session.proxy, "anvil-fixture ping");

		assertEquals(1, session.sent.size());
		assertEquals("anvil-fixture ping", assertInstanceOf(ServerboundChatCommandPacket.class, session.sent.getFirst()).getCommand());
	}

	@Test
	void flattensSystemPlayerAndDisguisedChat() {
		NativeSession session = new NativeSession();
		List<String> received = new ArrayList<>();
		packets.listen(session.proxy, received::add);

		session.receive(new ClientboundSystemChatPacket(Component.translatable("multiplayer.player.joined", Component.text("Alice"))
				.append(Component.text("!")), false));
		session.receive(playerChat("signed hello", null));
		session.receive(playerChat("signed hello", Component.text("decorated ").append(Component.text("hello"))));
		session.receive(new ClientboundKeepAlivePacket(7));
		session.receive(new ClientboundDisguisedChatPacket(Component.text("journey-ready-1"), Holder.ofId(1), Component.text("Server"), null));

		assertEquals(List.of("multiplayer.player.joined Alice!", "signed hello", "decorated hello", "journey-ready-1"), received);
	}

	@Test
	void flattensKeybindsAndSelectorsWithoutScores() {
		NativeSession session = new NativeSession();
		List<String> received = new ArrayList<>();
		packets.listen(session.proxy, received::add);

		session.receive(new ClientboundSystemChatPacket(Component.text("press ").append(Component.keybind("key.jump"))
				.append(Component.text(" near ")).append(Component.selector("@p"))
				.append(Component.score("Alice", "kills")).append(Component.text("!")), false));

		assertEquals(List.of("press key.jump near @p!"), received);
	}

	@Test
	void reportsOverlaySystemChatShownAboveTheHotbar() {
		NativeSession session = new NativeSession();
		List<String> received = new ArrayList<>();
		packets.listen(session.proxy, received::add);

		session.receive(new ClientboundSystemChatPacket(Component.text("anvil:action-bar"), true));

		assertEquals(List.of("anvil:action-bar"), received);
	}

	@Test
	void reportsNothingWhileDisconnectedAndRemovesItsListenerWhenDetached() {
		NativeSession session = new NativeSession();
		List<String> received = new ArrayList<>();
		Runnable detach = packets.listen(session.proxy, received::add);
		assertEquals(1, session.listeners.size());

		session.connected = false;
		session.receive(new ClientboundSystemChatPacket(Component.text("late"), false));
		detach.run();

		assertEquals(List.of(), received);
		assertEquals(List.of(), session.listeners);
	}

	@Test
	void isTheOnlyMessagesPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(MessagesPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolMessagesPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	private static ClientboundPlayerChatPacket playerChat(String content, @Nullable Component unsignedContent) {
		return new ClientboundPlayerChatPacket(0, new UUID(0, 1), 0, null, content, 0L, 0L, List.of(), unsignedContent,
				ChatFilterType.PASS_THROUGH, Holder.ofId(0), Component.text("Alice"), null);
	}

	/**
	 * An MCProtocolLib session that records sent packets and listeners and delivers received packets synchronously.
	 */
	private static final class NativeSession {
		private final List<Packet> sent = new ArrayList<>();
		private final List<SessionListener> listeners = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(instance, method, arguments) -> handle(method, arguments));
		private boolean connected = true;

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
		}

		private Object handle(Method method, Object[] arguments) {
			switch (method.getName()) {
				case "send" -> sent.add((Packet) arguments[0]);
				case "addListener" -> listeners.add((SessionListener) arguments[0]);
				case "removeListener" -> listeners.remove((SessionListener) arguments[0]);
				case "isConnected" -> {
					return connected;
				}
				default -> throw new UnsupportedOperationException(method.getName());
			}
			return null;
		}
	}
}
