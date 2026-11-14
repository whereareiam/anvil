package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TcpClientSessionsTest {
	private static final ClientLogin LOGIN = ClientLogin.builder()
			.profile(ClientProfile.builder().name("Alice").uniqueId(new UUID(0, 1)).build())
			.settings(ClientSettings.builder().locale("en_us").viewDistance(8).build())
			.connection(ClientConnection.builder().host("127.0.0.1").port(25565).build())
			.build();

	@Test
	void startsTheConfigurationAfterTheLibrarysOwnListenerAndAnswersKeepAlivesItself() {
		Session session = new TcpClientSessions(new TextTranslator())
				.open(LOGIN, new IgnoringListener(), McProtocolClientAdapter.packets());

		((MinecraftProtocol) session.getPacketProtocol()).newClientSession(session, false);

		// MCProtocolLib's own listener shares its simple name with Anvil's client listener.
		assertEquals(List.of(KeepAliveAnswers.class, ClientPacketListener.class,
				org.geysermc.mcprotocollib.protocol.ClientListener.class, ClientConfiguration.class), listeners(session));
	}

	private static List<Class<?>> listeners(Session session) {
		return session.getListeners().stream()
				.<Class<?>>map(SessionListener::getClass)
				.toList();
	}

	private static final class IgnoringListener implements ClientListener<Session> {
		@Override
		public void loggedIn(@NotNull Session session, @NotNull ClientProfile granted) {
		}

		@Override
		public void teleported(@NotNull Session session, float yaw, float pitch) {
		}

		@Override
		public void disconnected(@NotNull Session session, @NotNull DisconnectCause cause, @NotNull String reason) {
		}

		@Override
		public void received(@NotNull Session session, @NotNull ClientboundPacket packet) {
		}
	}
}
