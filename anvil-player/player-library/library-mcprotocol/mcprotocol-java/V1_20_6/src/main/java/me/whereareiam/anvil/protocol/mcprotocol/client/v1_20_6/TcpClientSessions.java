package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.service.SessionService;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.jetbrains.annotations.NotNull;

/**
 * Sessions of the MCProtocolLib 1.20.6 release: TCP sessions whose profiles and session service still come from
 * mcauthlib, which MCProtocolLib 1.21 replaced with its own. A session binds the source address through its
 * bind-address constructor; the handshake always announces the session host, so {@link ClientPacketListener}
 * rewrites the handshake for a virtual host.
 */
final class TcpClientSessions implements ClientSessions<Session> {
	private static final int ANY_PORT = 0;
	/**
	 * Separates the session server's address from the paths the session service appends to it.
	 */
	private static final String PATH_SEPARATOR = "/";

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
				credentials == null ? null : credentials.getAccessToken());
		Session session = connection.getSourceAddress() == null
				? new TcpClientSession(connection.getHost(), connection.getPort(), protocol)
				: new TcpClientSession(connection.getHost(), connection.getPort(), connection.getSourceAddress(),
						ANY_PORT, protocol);
		if (credentials != null && credentials.getSessionServer() != null) {
			SessionService sessionService = new SessionService();
			sessionService.setBaseUri(credentials.getSessionServer() + PATH_SEPARATOR);
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
		}
		session.addListener(new ClientPacketListener(login, listener, packets, TcpClientSessions::granted));

		return session;
	}

	@Override
	public void connect(@NotNull Session session) {
		session.connect(true);
	}

	/**
	 * Reads the profile a login success grants, which the release names with mcauthlib's profile.
	 */
	static ClientProfile granted(ClientboundGameProfilePacket success) {
		GameProfile profile = success.getProfile();
		return ClientProfile.builder().name(profile.getName()).uniqueId(profile.getId()).build();
	}
}
