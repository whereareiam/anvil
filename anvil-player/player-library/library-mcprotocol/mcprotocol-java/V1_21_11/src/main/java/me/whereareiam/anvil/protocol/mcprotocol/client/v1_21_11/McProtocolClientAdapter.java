package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

/**
 * Client of MCProtocolLib 1.21.11: client sessions from the network session factory, built-in profiles,
 * particle settings, the player-loaded acknowledgement after each teleport and adventure 4.25 without its
 * plain-text serializer. The handshake announces a virtual host through the client-host flag, and the session
 * binds the source address as its bind socket address; the factory's {@code setBindAddress} is avoided because
 * it replaces the remote address instead.
 */
public final class McProtocolClientAdapter implements McProtocolClient<ClientSession> {
	@Override
	public int protocolNumber() {
		return MinecraftCodec.CODEC.getProtocolVersion();
	}

	@Override
	public @NotNull Class<ClientSession> sessionType() {
		return ClientSession.class;
	}

	@Override
	public @NotNull ClientSession open(@NotNull ClientLogin login, @NotNull ClientListener<? super ClientSession> listener) {
		ClientProfile profile = login.getProfile();
		ClientCredentials credentials = login.getCredentials();
		ClientConnection connection = login.getConnection();
		ClientSession session = ClientNetworkSessionFactory.factory()
				.setAddress(connection.getHost(), connection.getPort())
				.setBindSocketAddress(connection.getSourceAddress() == null ? null : new InetSocketAddress(connection.getSourceAddress(), 0))
				.setProtocol(new MinecraftProtocol(new GameProfile(profile.getUniqueId(), profile.getName()),
						credentials == null ? null : credentials.getAccessToken()))
				.create();
		if (connection.getVirtualHost() != null)
			session.setFlag(MinecraftConstants.CLIENT_HOST, connection.getVirtualHost());
		if (credentials != null && credentials.getSessionServer() != null)
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, new RedirectedSessionService(credentials.getSessionServer()));
		session.addListener(new ClientPacketListener(session, login, listener));
		return session;
	}

	@Override
	public void connect(@NotNull ClientSession session) {
		session.connect(true);
	}

	@Override
	public boolean connected(@NotNull ClientSession session) {
		return session.isConnected();
	}

	@Override
	public void disconnect(@NotNull ClientSession session, @NotNull String reason) {
		session.disconnect(reason);
	}
}
