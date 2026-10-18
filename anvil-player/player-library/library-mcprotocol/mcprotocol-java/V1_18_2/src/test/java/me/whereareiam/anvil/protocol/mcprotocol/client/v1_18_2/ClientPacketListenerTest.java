package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.protocol.data.game.MessageType;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.data.handshake.HandshakeIntent;
import com.github.steveice10.mc.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundDisconnectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundCustomPayloadPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import com.github.steveice10.mc.protocol.packet.login.clientbound.ClientboundLoginDisconnectPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.PacketSendingEvent;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientBrand;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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
			McProtocolClientAdapter.packets(), McProtocolClientAdapter::reason);

	@Test
	void answersTheLoginWithClientInformationAndTheBrandBeforeReportingItWithTheGrantedProfile() {
		receive(new ClientboundGameProfilePacket(new GameProfile(GRANTED, "Alice_Renamed")));
		CompoundTag type = new CompoundTag("");
		type.put(new IntTag("min_y", -64));
		type.put(new IntTag("height", 384));
		receive(new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL, 1, new String[0],
				new CompoundTag(""), type, "minecraft:overworld", 0, 20, 10, 10, false, true, false, false));

		assertEquals(List.of("granted Alice_Renamed " + GRANTED, "sent ServerboundClientInformationPacket",
				"sent ServerboundCustomPayloadPacket", "logged in as Alice_Renamed " + GRANTED), events);
		ServerboundClientInformationPacket information = assertInstanceOf(ServerboundClientInformationPacket.class, sent.getFirst());
		assertEquals("en_us", information.getLocale());
		assertEquals(8, information.getRenderDistance());
		assertEquals(ChatVisibility.FULL, information.getChatVisibility());
		assertEquals(Arrays.asList(SkinPart.values()), information.getVisibleParts());
		assertEquals(HandPreference.RIGHT_HAND, information.getMainHand());
		assertFalse(information.isTextFilteringEnabled());
		assertTrue(information.isAllowsListing());
		ServerboundCustomPayloadPacket brand = assertInstanceOf(ServerboundCustomPayloadPacket.class, sent.get(1));
		assertEquals("minecraft:brand", brand.getChannel());
		assertArrayEquals(ClientBrand.payload(), brand.getData());
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

	@Test
	void announcesTheVirtualHostInPlaceOfTheConnectedHost() {
		ClientLogin virtual = ClientLogin.builder()
				.profile(LOGIN.getProfile())
				.settings(LOGIN.getSettings())
				.connection(ClientConnection.builder().host("127.0.0.1").port(25577).virtualHost("lobby.example.test").build())
				.build();
		PacketSendingEvent handshake = new PacketSendingEvent(session, new ClientIntentionPacket(1, "127.0.0.1", 25577, HandshakeIntent.LOGIN));

		new ClientPacketListener(virtual, new RecordingListener(), McProtocolClientAdapter.packets(),
				McProtocolClientAdapter::reason)
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
		receive(new ClientboundChatPacket(Component.text("anvil:welcome"), MessageType.SYSTEM));
		receive(new ClientboundKeepAlivePacket(7));

		assertEquals(List.of(new ClientboundSystemChat("anvil:welcome", false)), received);
		assertEquals(List.of(), events);
	}

	@Test
	void reportsTheGrantedProfileAsSoonAsTheLoginSucceeds() {
		receive(new ClientboundGameProfilePacket(new GameProfile(GRANTED, "Alice_Renamed")));

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
