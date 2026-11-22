package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.player.library.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.TestClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ClientPacketListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.KnownPacksAnswer;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.TextTranslator;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TcpClientSessionsTest {
	private static final ClientLogin LOGIN = ClientLogin.builder()
			.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
			.minecraftVersion("1.21.1")
			.settings(ClientSettings.builder().locale("en_us").viewDistance(8).build())
			.connection(ClientConnection.builder().host("127.0.0.1").port(25565).build())
			.build();

	@Test
	void readsTheProfileTheLoginSuccessGrantsInTheLibrarysOwnProfile() {
		UUID granted = new UUID(0, 2);

		ClientProfile profile = TcpClientSessions.granted(
				new ClientboundGameProfilePacket(new GameProfile(granted, "Alice_Renamed"), false));

		assertEquals(ClientProfile.builder().name("Alice_Renamed").uniqueId(granted).build(), profile);
	}

	@Test
	void startsTheConfigurationAfterTheLibrarysOwnListenerAnswersKnownPacksAndLeavesKeepAlivesToIt() {
		Session session = new TcpClientSessions(new TextTranslator())
				.open(LOGIN, new TestClientListener<>(), McProtocolClientAdapter.packets(PacketDemand.EVERY));

		((MinecraftProtocol) session.getPacketProtocol()).newClientSession(session, false);

		// MCProtocolLib's own listener shares its simple name with Anvil's client listener.
		List<Class<?>> listeners = session.getListeners().stream()
				.<Class<?>>map(SessionListener::getClass)
				.toList();
		assertEquals(List.of(ClientPacketListener.class, org.geysermc.mcprotocollib.protocol.ClientListener.class,
				ClientConfiguration.class, KnownPacksAnswer.class), listeners);
		assertFalse(session.getFlag(MinecraftConstants.SEND_BLANK_KNOWN_PACKS_RESPONSE, true));
	}
}
