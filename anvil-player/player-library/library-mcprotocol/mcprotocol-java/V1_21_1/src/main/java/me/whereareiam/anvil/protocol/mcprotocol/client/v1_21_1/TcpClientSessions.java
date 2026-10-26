package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ClientPacketListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ClientSessions;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.jetbrains.annotations.NotNull;

/**
 * Sessions of the MCProtocolLib 1.21.1 release: TCP sessions with the profiles and session service MCProtocolLib brings
 * itself since 1.21, which a scenario's session server replaces. A session binds the source address through its
 * bind-address constructor; the handshake always announces the session host, so {@link ClientPacketListener} rewrites
 * the handshake for a virtual host. MCProtocolLib adds its own listener when a session connects, and the session's
 * protocol adds the {@link ConfigurationListener} right after it.
 */
final class TcpClientSessions implements ClientSessions<Session> {
	private static final int ANY_PORT = 0;

	@Override
	public @NotNull Class<Session> type() {
		return Session.class;
	}

	@Override
	public @NotNull Session open(
			@NotNull ClientLogin login,
			@NotNull ClientListener<? super Session> listener,
			@NotNull PacketTable<Packet> packets
	) {
		ClientProfile profile = login.getProfile();
		ClientCredentials credentials = login.getCredentials();
		ClientConnection connection = login.getConnection();
		MinecraftProtocol protocol = new MinecraftProtocol(new GameProfile(profile.getUniqueId(), profile.getName()),
				credentials == null ? null : credentials.getAccessToken()) {
			@Override
			public void newClientSession(Session session, boolean transferring) {
				super.newClientSession(session, transferring);
				session.addListener(new ConfigurationListener(login.getSettings()));
			}
		};
		Session session = connection.getSourceAddress() == null
				? new TcpClientSession(connection.getHost(), connection.getPort(), protocol)
				: new TcpClientSession(connection.getHost(), connection.getPort(), connection.getSourceAddress(),
						ANY_PORT, protocol);
		if (credentials != null && credentials.getSessionServer() != null)
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY,
					new RedirectedSessionService(credentials.getSessionServer()));
		session.addListener(new ClientPacketListener(login, listener, packets, TcpClientSessions::granted));

		return session;
	}

	@Override
	public void connect(@NotNull Session session) {
		session.connect(true);
	}

	/**
	 * Reads the profile a login success grants, which the release names with MCProtocolLib's profile.
	 */
	static ClientProfile granted(ClientboundGameProfilePacket success) {
		GameProfile profile = success.getProfile();
		return ClientProfile.builder().name(profile.getName()).uniqueId(profile.getId()).build();
	}
}
