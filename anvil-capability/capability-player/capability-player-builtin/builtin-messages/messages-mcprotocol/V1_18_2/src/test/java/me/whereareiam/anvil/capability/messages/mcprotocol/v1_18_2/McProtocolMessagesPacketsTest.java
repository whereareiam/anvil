package me.whereareiam.anvil.capability.messages.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.MessageType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.capability.messages.packet.MessagesPackets;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class McProtocolMessagesPacketsTest {
	private final McProtocolMessagesPackets packets = new McProtocolMessagesPackets();

	@Test
	void sendsChatAsOneChatPacket() {
		NativeSession session = new NativeSession();

		packets.chat(session.proxy, "hello there");

		assertEquals(1, session.sent.size());
		assertEquals("hello there", assertInstanceOf(ServerboundChatPacket.class, session.sent.getFirst()).getMessage());
	}

	@Test
	void sendsCommandsAsChatWithALeadingSlash() {
		NativeSession session = new NativeSession();

		packets.command(session.proxy, "anvil-fixture ping");

		assertEquals(1, session.sent.size());
		assertEquals("/anvil-fixture ping", assertInstanceOf(ServerboundChatPacket.class, session.sent.getFirst()).getMessage());
	}

	@Test
	void flattensReceivedChatWithTranslationArgumentsAndChildren() {
		NativeSession session = new NativeSession();
		List<String> received = new ArrayList<>();
		packets.listen(session.proxy, received::add);

		session.receive(new ClientboundChatPacket(Component.translatable("chat.type.text", Component.text("Alice"), Component.text("hello"))
				.append(Component.text("!"))));
		session.receive(new ClientboundKeepAlivePacket(7));
		session.receive(new ClientboundChatPacket(Component.text("anvil:").append(Component.text("welcome"))));

		assertEquals(List.of("chat.type.text Alice hello!", "anvil:welcome"), received);
	}

	@Test
	void flattensKeybindsAndSelectorsWithoutScores() {
		NativeSession session = new NativeSession();
		List<String> received = new ArrayList<>();
		packets.listen(session.proxy, received::add);

		session.receive(new ClientboundChatPacket(Component.text("press ").append(Component.keybind("key.jump"))
				.append(Component.text(" near ")).append(Component.selector("@p"))
				.append(Component.score("Alice", "kills")).append(Component.text("!"))));

		assertEquals(List.of("press key.jump near @p!"), received);
	}

	@Test
	void reportsGameInfoChatShownAboveTheHotbar() {
		NativeSession session = new NativeSession();
		List<String> received = new ArrayList<>();
		packets.listen(session.proxy, received::add);

		session.receive(new ClientboundChatPacket(Component.text("anvil:action-bar"), MessageType.NOTIFICATION));

		assertEquals(List.of("anvil:action-bar"), received);
	}

	@Test
	void reportsNothingWhileDisconnectedAndRemovesItsListenerWhenDetached() {
		NativeSession session = new NativeSession();
		List<String> received = new ArrayList<>();
		Runnable detach = packets.listen(session.proxy, received::add);
		assertEquals(1, session.listeners.size());

		session.connected = false;
		session.receive(new ClientboundChatPacket(Component.text("late")));
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

	/**
	 * A packetlib session that records sent packets and listeners and delivers received packets synchronously.
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
