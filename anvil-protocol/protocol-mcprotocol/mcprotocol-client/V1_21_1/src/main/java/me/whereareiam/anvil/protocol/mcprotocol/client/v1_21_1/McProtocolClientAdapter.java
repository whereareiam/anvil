package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Client of MCProtocolLib 1.21.1: the GeyserMC packages with TCP sessions, built-in profiles and adventure
 * 4.15 without its plain-text serializer. The session binds the source address through its bind-address
 * constructor; the handshake always announces the session host, so {@link ClientPacketListener} rewrites the
 * handshake for a virtual host.
 */
public final class McProtocolClientAdapter implements McProtocolClient<Session> {
	@Override
	public int protocolNumber() {
		return MinecraftCodec.CODEC.getProtocolVersion();
	}

	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public @NotNull Session open(@NotNull ClientLogin login, @NotNull ClientListener<? super Session> listener) {
		ClientProfile profile = login.getProfile();
		ClientCredentials credentials = login.getCredentials();
		ClientConnection connection = login.getConnection();
		MinecraftProtocol protocol = new MinecraftProtocol(new GameProfile(profile.getUniqueId(), profile.getName()),
				credentials == null ? null : credentials.getAccessToken());
		Session session = connection.getSourceAddress() == null
				? new TcpClientSession(connection.getHost(), connection.getPort(), protocol)
				: new TcpClientSession(connection.getHost(), connection.getPort(), connection.getSourceAddress(), 0, protocol);
		if (credentials != null && credentials.getSessionServer() != null)
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, new RedirectedSessionService(credentials.getSessionServer()));
		session.addListener(new ClientPacketListener(login, listener));
		return session;
	}

	@Override
	public void connect(@NotNull Session session) {
		session.connect(true);
	}

	@Override
	public boolean connected(@NotNull Session session) {
		return session.isConnected();
	}

	@Override
	public void disconnect(@NotNull Session session, @NotNull String reason) {
		session.disconnect(reason);
	}
}
