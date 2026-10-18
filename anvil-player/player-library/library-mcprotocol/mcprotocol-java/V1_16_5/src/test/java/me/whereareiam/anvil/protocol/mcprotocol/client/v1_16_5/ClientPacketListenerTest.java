package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.protocol.data.game.MessageType;
import com.github.steveice10.mc.protocol.data.handshake.HandshakeIntent;
import com.github.steveice10.mc.protocol.packet.handshake.client.HandshakePacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientSettingsPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientConfirmTransactionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerConfirmTransactionPacket;
import com.github.steveice10.mc.protocol.packet.login.server.EncryptionRequestPacket;
import com.github.steveice10.mc.protocol.packet.login.server.LoginSuccessPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.PacketSendingEvent;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPacketListenerTest {
	private static final UUID GRANTED = new UUID(0, 2);
	private static final ClientLogin LOGIN = ClientLogin.builder()
			.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
			.settings(ClientSettings.builder().locale("en_us").viewDistance(8).build())
			.connection(ClientConnection.builder().host("localhost").port(25565).build())
			.build();

	private final List<String> events = new ArrayList<>();
	private final List<Packet> sent = new ArrayList<>();
	private final List<ClientboundPacket> received = new ArrayList<>();
	private final Session session = session();
	private final ClientPacketListener listener = new ClientPacketListener(LOGIN, new RecordingListener(),
			McProtocolClientAdapter.packets(), new ReleaseAnswers());

	@Test
	void answersTheJoinWithClientSettingsBeforeReportingTheLoginWithTheGrantedProfile() throws Exception {
		receive(new LoginSuccessPacket(new GameProfile(GRANTED, "Alice_Renamed")));
		receive(allocate(ServerJoinGamePacket.class));

		assertEquals(List.of("granted Alice_Renamed " + GRANTED, "sent ClientSettingsPacket", "logged in as Alice_Renamed " + GRANTED), events);
		ClientSettingsPacket settings = assertInstanceOf(ClientSettingsPacket.class, sent.getFirst());
		assertEquals("en_us", settings.getLocale());
		assertEquals(8, settings.getRenderDistance());
	}

	@Test
	void reportsATeleportBeforeConfirmingIt() {
		receive(new ServerPlayerPositionRotationPacket(1, 64, -2, 90, -15, 7));

		assertEquals(List.of("teleported 90.0 -15.0", "sent ClientTeleportConfirmPacket"), events);
		assertEquals(7, assertInstanceOf(ClientTeleportConfirmPacket.class, sent.getFirst()).getId());
	}

	@Test
	void acknowledgesARejectedTransactionAndNothingElse() {
		receive(new ServerConfirmTransactionPacket(3, 12, true));
		receive(new ServerConfirmTransactionPacket(3, 13, false));

		ClientConfirmTransactionPacket acknowledged = assertInstanceOf(ClientConfirmTransactionPacket.class,
				sent.getFirst());
		assertEquals(1, sent.size());
		assertEquals(3, acknowledged.getWindowId());
		assertEquals(13, acknowledged.getActionId());
		assertTrue(acknowledged.isAccepted());
	}

	@Test
	void reportsAServerThatAsksForEncryptionFromAClientWithoutAnAccountAsWantingAuthentication() throws Exception {
		KeyPairGenerator keys = KeyPairGenerator.getInstance("RSA");
		keys.initialize(1024);
		receive(new EncryptionRequestPacket("", keys.generateKeyPair().getPublic(), new byte[4]));

		listener.disconnected(new DisconnectedEvent(session, "Disconnected"));

		assertEquals(List.of("disconnected AUTHENTICATION_REQUIRED Disconnected"), events);
	}

	@Test
	void flattensTheReasonOfTheServersDisconnect() {
		receive(new ServerDisconnectPacket(Component.translatable("multiplayer.disconnect.kicked",
				Component.text("Alice"))));
		listener.disconnected(new DisconnectedEvent(session, "Closed"));

		assertEquals(List.of("disconnected SERVER multiplayer.disconnect.kicked Alice",
				"disconnected CONNECTION_LOST Closed"), events);
	}

	@Test
	void announcesTheVirtualHostInPlaceOfTheConnectedHost() {
		ClientLogin virtual = ClientLogin.builder()
				.profile(LOGIN.getProfile())
				.settings(LOGIN.getSettings())
				.connection(ClientConnection.builder().host("127.0.0.1").port(25577).virtualHost("lobby.example.test")
						.build())
				.build();
		PacketSendingEvent handshake = new PacketSendingEvent(session,
				new HandshakePacket(754, "127.0.0.1", 25577, HandshakeIntent.LOGIN));

		new ClientPacketListener(virtual, new RecordingListener(), McProtocolClientAdapter.packets(),
				new ReleaseAnswers()).packetSending(handshake);

		HandshakePacket announced = assertInstanceOf(HandshakePacket.class, handshake.getPacket());
		assertEquals("lobby.example.test", announced.getHostname());
		assertEquals(25577, announced.getPort());
	}

	@Test
	void handsEveryTranslatedPacketToTheListener() {
		receive(new ServerChatPacket(Component.text("anvil:welcome"), MessageType.SYSTEM));

		assertEquals(List.of(new ClientboundSystemChat("anvil:welcome", false)), received);
	}

	@Test
	void reportsTheGrantedProfileAsSoonAsTheLoginSucceeds() {
		receive(new LoginSuccessPacket(new GameProfile(GRANTED, "Alice_Renamed")));

		assertEquals(List.of("granted Alice_Renamed " + GRANTED), events);
	}

	private void receive(Packet packet) {
		listener.packetReceived(new PacketReceivedEvent(session, packet));
	}

	private Session session() {
		Class<?>[] types = {Session.class};
		return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), types, (proxy, method, arguments) -> {
			if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

			Packet packet = (Packet) arguments[0];
			sent.add(packet);
			events.add("sent " + packet.getClass().getSimpleName());
			return null;
		});
	}

	/**
	 * Creates a packet without running its constructor, for packets the listener only recognizes by type.
	 */
	private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		Field instance = unsafe.getDeclaredField("theUnsafe");
		instance.setAccessible(true);
		return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type));
	}

	private final class RecordingListener implements ClientListener<Session> {
		@Override
		public void granted(@NotNull Session session, @NotNull ClientProfile granted) {
			events.add("granted " + granted.getName() + " " + granted.getUniqueId());
		}

		@Override
		public void loggedIn(@NotNull Session session, @NotNull ClientProfile granted) {
			events.add("logged in as " + granted.getName() + " " + granted.getUniqueId());
		}

		@Override
		public void teleported(@NotNull Session session, float yaw, float pitch) {
			events.add("teleported " + yaw + " " + pitch);
		}

		@Override
		public void disconnected(@NotNull Session session, @NotNull DisconnectCause cause, @NotNull String reason) {
			events.add("disconnected " + cause + " " + reason);
		}

		@Override
		public void received(@NotNull Session session, @NotNull ClientboundPacket packet) {
			received.add(packet);
		}
	}
}
