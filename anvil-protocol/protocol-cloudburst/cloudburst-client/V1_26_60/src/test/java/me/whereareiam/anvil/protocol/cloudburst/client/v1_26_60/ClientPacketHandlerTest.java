package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import io.netty.channel.embedded.EmbeddedChannel;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientListener;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ClientPacketHandlerTest {
	private static final UUID CLAIMED = new UUID(0, 1);
	private static final UUID LISTED = new UUID(0, 2);

	private final List<String> callbacks = new ArrayList<>();
	private ConnectionSession connection;

	@Test
	void logsInOfflineWithASelfSignedChainAndTheClientDataOfItsCodec() throws Exception {
		RecordingSession session = session(Bedrock_v2193.CODEC);
		ClientPacketHandler handler = handler(session, Bedrock_v2193.CODEC, new TestIdentity(null));

		NetworkSettingsPacket settings = new NetworkSettingsPacket();
		settings.setCompressionThreshold(1);
		settings.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
		handler.handle(settings);

		assertEquals(PacketCompressionAlgorithm.ZLIB, session.compression);
		LoginPacket login = assertInstanceOf(LoginPacket.class, session.sent.getFirst());
		assertEquals(2193, login.getProtocolVersion());
		CertificateChainPayload chain = assertInstanceOf(CertificateChainPayload.class, login.getAuthPayload());
		assertEquals(List.of("chain-link"), chain.getChain());
		assertEquals(AuthType.SELF_SIGNED, chain.getAuthType());
		assertEquals("client-data 1.26.50 127.0.0.1:19132", login.getClientJwt());
	}

	@Test
	void sendsTheMultiplayerTokenOfAnXboxIdentityOnlyToCodecsThatTakeIt() throws Exception {
		RecordingSession current = session(Bedrock_v2193.CODEC);
		handler(current, Bedrock_v2193.CODEC, new TestIdentity("token")).handle(new NetworkSettingsPacket());
		TokenPayload token = assertInstanceOf(TokenPayload.class, ((LoginPacket) current.sent.getFirst()).getAuthPayload());
		assertEquals("token", token.getToken());
		assertEquals(AuthType.FULL, token.getAuthType());

		RecordingSession older = session(Bedrock_v800.CODEC);
		handler(older, Bedrock_v800.CODEC, new TestIdentity("token")).handle(new NetworkSettingsPacket());
		CertificateChainPayload chain = assertInstanceOf(CertificateChainPayload.class, ((LoginPacket) older.sent.getFirst()).getAuthPayload());
		assertEquals(AuthType.FULL, chain.getAuthType());
	}

	@Test
	void announcesTheInitializedPlayerAndReportsTheProfileTheServerListsForIt() throws Exception {
		RecordingSession session = session(Bedrock_v2193.CODEC);
		ClientPacketHandler handler = handler(session, Bedrock_v2193.CODEC, new TestIdentity(null));

		StartGamePacket start = new StartGamePacket();
		start.setRuntimeEntityId(7);
		start.setUniqueEntityId(70);
		start.setRotation(Vector2f.from(10, 90));
		start.setPlayerPosition(Vector3f.from(1, 2, 3));
		handler.handle(start);
		PlayerListPacket list = new PlayerListPacket();
		PlayerListPacket.Entry entry = new PlayerListPacket.Entry(LISTED);
		entry.setAction(PlayerListPacket.Action.ADD);
		entry.setEntityId(70);
		entry.setName("Renamed");
		list.getEntries().add(entry);
		handler.handle(list);
		PlayStatusPacket spawn = new PlayStatusPacket();
		spawn.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
		handler.handle(spawn);

		SetLocalPlayerAsInitializedPacket initialized = assertInstanceOf(SetLocalPlayerAsInitializedPacket.class, session.sent.getLast());
		assertEquals(7, initialized.getRuntimeEntityId());
		assertEquals(List.of("view 90.0 10.0", "login Renamed " + LISTED), callbacks);
	}

	@Test
	void reportsARefusedLoginAndAnAuthenticationKickWithTheirCause() throws Exception {
		RecordingSession session = session(Bedrock_v2193.CODEC);
		ClientPacketHandler handler = handler(session, Bedrock_v2193.CODEC, new TestIdentity(null));

		PlayStatusPacket refused = new PlayStatusPacket();
		refused.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);
		handler.handle(refused);
		DisconnectPacket kick = new DisconnectPacket();
		kick.setKickMessage("disconnectionScreen.notAuthenticated");
		handler.handle(kick);

		assertEquals(List.of(
				"disconnect SERVER The server refused the login: LOGIN_FAILED_CLIENT_OLD",
				"disconnect AUTHENTICATION_REQUIRED disconnectionScreen.notAuthenticated"
		), callbacks);
		assertEquals(List.of("The server refused the login: LOGIN_FAILED_CLIENT_OLD"), session.disconnects);
	}

	private ClientPacketHandler handler(RecordingSession session, BedrockCodec codec, TestIdentity identity) {
		ClientLogin login = ClientLogin.builder()
				.profile(ClientProfile.builder().name("Alice").uniqueId(CLAIMED).build())
				.identity(identity)
				.connection(ClientConnection.builder().host("127.0.0.1").port(19132).protocol(codec.getProtocolVersion()).build())
				.build();
		connection = new ConnectionSession(session.getPeer(), codec, login.getProfile());
		connection.attach(session);
		return new ClientPacketHandler(session, connection, codec, login, new Recorder());
	}

	private static RecordingSession session(BedrockCodec codec) {
		BedrockPacketCodec_v3 packets = new BedrockPacketCodec_v3();
		packets.setCodec(codec);
		return new RecordingSession(new BedrockPeer(new EmbeddedChannel(packets), (peer, subClientId) -> null));
	}

	/**
	 * Records what the handler reports.
	 */
	private final class Recorder implements ClientListener<ConnectionSession> {
		@Override
		public void loggedIn(@NotNull ConnectionSession session, @NotNull ClientProfile granted) {
			callbacks.add("login " + granted.getName() + " " + granted.getUniqueId());
		}

		@Override
		public void teleported(@NotNull ConnectionSession session, float yaw, float pitch) {
			callbacks.add("view " + yaw + " " + pitch);
		}

		@Override
		public void disconnected(@NotNull ConnectionSession session, @NotNull DisconnectCause cause, @NotNull String reason) {
			callbacks.add("disconnect " + cause + " " + reason);
		}
	}

	/**
	 * A session that records what the handler sends instead of writing it.
	 */
	private static final class RecordingSession extends BedrockClientSession {
		private final List<BedrockPacket> sent = new ArrayList<>();
		private final List<String> disconnects = new ArrayList<>();
		private PacketCompressionAlgorithm compression;

		private RecordingSession(BedrockPeer peer) {
			super(peer, 0);
		}

		@Override
		public void sendPacket(@NotNull BedrockPacket packet) {
			sent.add(packet);
		}

		@Override
		public void sendPacketImmediately(@NotNull BedrockPacket packet) {
			sent.add(packet);
		}

		@Override
		public void setCompression(PacketCompressionAlgorithm algorithm) {
			compression = algorithm;
		}

		@Override
		public void disconnect(CharSequence reason, boolean hideReason) {
			disconnects.add(reason.toString());
		}
	}
}
