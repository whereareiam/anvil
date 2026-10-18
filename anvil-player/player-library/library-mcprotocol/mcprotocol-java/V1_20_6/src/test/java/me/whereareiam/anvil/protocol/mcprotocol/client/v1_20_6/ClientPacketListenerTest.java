package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import com.github.steveice10.mc.auth.data.GameProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.data.handshake.HandshakeIntent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientPacketListenerTest {
	private static final UUID GRANTED = new UUID(0, 2);
	private static final ClientLogin LOGIN = ClientLogin.builder()
			.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
			.settings(ClientSettings.builder().locale("en_us").viewDistance(8).build())
			.connection(ClientConnection.builder().host("localhost").port(25565).build())
			.build();
	private static final ClientboundRegistryDataPacket DIMENSION_TYPES = new ClientboundRegistryDataPacket(
			Key.key("minecraft:dimension_type"), List.of(new RegistryEntry(Key.key("minecraft:overworld"),
					NbtMap.builder().putInt("min_y", -64).putInt("height", 384).build())));

	private final List<String> events = new ArrayList<>();
	private final List<Packet> sent = new ArrayList<>();
	private final List<ClientboundPacket> received = new ArrayList<>();
	private final Session session = session();
	private final ClientPacketListener listener = new ClientPacketListener(LOGIN, new RecordingListener(),
			McProtocolClientAdapter.packets(), TcpClientSessions::granted);

	@Test
	void answersTheLoginWithClientInformationBeforeReportingItWithTheGrantedProfile() {
		receive(new ClientboundGameProfilePacket(new GameProfile(GRANTED, "Alice_Renamed"), false));
		receive(DIMENSION_TYPES);
		receive(new ClientboundLoginPacket(42, false, new Key[0], 20, 10, 10, false, true, false,
				new PlayerSpawnInfo(0, Key.key("minecraft:overworld"), 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false,
						false, null, 0), false));

		assertEquals(List.of("granted Alice_Renamed " + GRANTED, "sent ServerboundClientInformationPacket", "logged in as Alice_Renamed " + GRANTED), events);
		ServerboundClientInformationPacket information = assertInstanceOf(ServerboundClientInformationPacket.class, sent.getFirst());
		assertEquals("en_us", information.getLocale());
		assertEquals(8, information.getRenderDistance());
		assertEquals(ChatVisibility.FULL, information.getChatVisibility());
		assertEquals(Arrays.asList(SkinPart.values()), information.getVisibleParts());
		assertEquals(HandPreference.RIGHT_HAND, information.getMainHand());
	}

	@Test
	void reportsATeleportBeforeAcceptingIt() {
		receive(new ClientboundPlayerPositionPacket(1, 64, -2, 90, -15, 7));

		assertEquals(List.of("teleported 90.0 -15.0", "sent ServerboundAcceptTeleportationPacket"), events);
		assertEquals(7, assertInstanceOf(ServerboundAcceptTeleportationPacket.class, sent.getFirst()).getId());
	}

	@Test
	void flattensDisconnectReasonsToPlainTextWithTranslationArguments() {
		receive(new ClientboundDisconnectPacket(Component.translatable("multiplayer.disconnect.kicked", Component.text("Alice"))
				.append(Component.text(" by Anvil"))));
		receive(new ClientboundLoginDisconnectPacket(Component.text("Outdated: ").append(Component.translatable("version", Component.text("1.20.6")))));

		assertEquals(List.of("disconnected SERVER multiplayer.disconnect.kicked Alice by Anvil", "disconnected SERVER Outdated: version 1.20.6"), events);
	}

	@Test
	void announcesTheVirtualHostInPlaceOfTheConnectedHost() {
		ClientLogin virtual = ClientLogin.builder()
				.profile(LOGIN.getProfile())
				.settings(LOGIN.getSettings())
				.connection(ClientConnection.builder().host("127.0.0.1").port(25577).virtualHost("lobby.example.test").build())
				.build();
		PacketSendingEvent handshake = new PacketSendingEvent(session, new ClientIntentionPacket(1, "127.0.0.1", 25577, HandshakeIntent.LOGIN));

		new ClientPacketListener(virtual, new RecordingListener(), McProtocolClientAdapter.packets(),
				TcpClientSessions::granted)
				.packetSending(handshake);

		ClientIntentionPacket announced = assertInstanceOf(ClientIntentionPacket.class, handshake.getPacket());
		assertEquals("lobby.example.test", announced.getHostname());
		assertEquals(25577, announced.getPort());
		assertEquals(HandshakeIntent.LOGIN, announced.getIntent());
	}

	@Test
	void leavesTheHandshakeAloneWithoutAVirtualHost() {
		ClientIntentionPacket intention = new ClientIntentionPacket(1, "localhost", 25565, HandshakeIntent.LOGIN);
		PacketSendingEvent handshake = new PacketSendingEvent(session, intention);

		listener.packetSending(handshake);

		assertSame(intention, handshake.getPacket());
	}

	@Test
	void handsEveryTranslatedPacketToTheListenerAndNothingForOthers() {
		receive(new ClientboundSystemChatPacket(Component.text("anvil:welcome"), false));
		receive(new ClientboundKeepAlivePacket(7));

		assertEquals(List.of(new ClientboundSystemChat("anvil:welcome", false)), received);
		assertEquals(List.of(), events);
	}

	@Test
	void reportsTheGrantedProfileAsSoonAsTheLoginSucceeds() {
		receive(new ClientboundGameProfilePacket(new GameProfile(GRANTED, "Alice_Renamed"), false));

		assertEquals(List.of("granted Alice_Renamed " + GRANTED), events);
	}

	private void receive(Packet packet) {
		listener.packetReceived(session, packet);
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
