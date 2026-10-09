package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import me.whereareiam.anvil.api.type.DisconnectCause;
import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClientPacketListenerTest {
	private static final ClientLogin LOGIN = ClientLogin.builder()
			.name("Alice")
			.uniqueId(new UUID(0, 1))
			.host("localhost")
			.port(25565)
			.locale("en_us")
			.viewDistance(8)
			.build();

	private final List<String> events = new ArrayList<>();
	private final List<Packet> sent = new ArrayList<>();
	private final Session session = session();
	private final ClientPacketListener listener = new ClientPacketListener(LOGIN, new RecordingListener());

	@Test
	void answersTheLoginWithClientInformationBeforeReportingIt() throws Exception {
		receive(allocate(ClientboundLoginPacket.class));

		assertEquals(List.of("sent ServerboundClientInformationPacket", "logged in"), events);
		ServerboundClientInformationPacket information = assertInstanceOf(ServerboundClientInformationPacket.class, sent.getFirst());
		assertEquals("en_us", information.getLocale());
		assertEquals(8, information.getRenderDistance());
		assertEquals(ChatVisibility.FULL, information.getChatVisibility());
		assertEquals(Arrays.asList(SkinPart.values()), information.getVisibleParts());
		assertEquals(HandPreference.RIGHT_HAND, information.getMainHand());
	}

	@Test
	void reportsATeleportBeforeAcceptingIt() {
		receive(new ClientboundPlayerPositionPacket(1, 64, -2, 90, -15, 7, false));

		assertEquals(List.of("teleported 90.0 -15.0", "sent ServerboundAcceptTeleportationPacket"), events);
		assertEquals(7, assertInstanceOf(ServerboundAcceptTeleportationPacket.class, sent.getFirst()).getId());
	}

	@Test
	void flattensDisconnectReasonsToPlainTextWithTranslationArguments() {
		receive(new ClientboundDisconnectPacket(Component.translatable("multiplayer.disconnect.kicked", Component.text("Alice"))
				.append(Component.text(" by Anvil"))));
		receive(new ClientboundLoginDisconnectPacket(Component.text("Outdated: ").append(Component.translatable("version", Component.text("1.18.2")))));

		assertEquals(List.of("disconnected SERVER multiplayer.disconnect.kicked Alice by Anvil", "disconnected SERVER Outdated: version 1.18.2"), events);
	}

	private void receive(Packet packet) {
		listener.packetReceived(session, packet);
	}

	private Session session() {
		return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class}, (proxy, method, arguments) -> {
			if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

			Packet packet = (Packet) arguments[0];
			sent.add(packet);
			events.add("sent " + packet.getClass().getSimpleName());
			return null;
		});
	}

	/**
	 * Creates a packet without running its constructor, for packets the listener only recognizes by type and whose
	 * constructors differ in every release.
	 */
	private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		Field instance = unsafe.getDeclaredField("theUnsafe");
		instance.setAccessible(true);
		return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type));
	}

	private final class RecordingListener implements ClientListener<Session> {
		@Override
		public void loggedIn(@NotNull Session session) {
			events.add("logged in");
		}

		@Override
		public void teleported(@NotNull Session session, float yaw, float pitch) {
			events.add("teleported " + yaw + " " + pitch);
		}

		@Override
		public void disconnected(@NotNull Session session, @NotNull DisconnectCause cause, @NotNull String reason) {
			events.add("disconnected " + cause + " " + reason);
		}
	}
}
