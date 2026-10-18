package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ClientSessions;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.RedirectedSessionService;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.function.BiFunction;

/**
 * Sessions of the MCProtocolLib releases from 1.21.11 on: client sessions from the network session factory with
 * built-in profiles. The handshake announces a virtual host through the client-host flag, and the session binds the
 * source address as its bind socket address; the factory's {@code setBindAddress} is avoided because it replaces the
 * remote address instead. How a client accepts a teleport changed in Minecraft 26.3, so each segment's adapter hands
 * over its release's teleport listener.
 */
public final class NetworkClientSessions implements ClientSessions<ClientSession> {
	private static final int ANY_PORT = 0;

	private final BiFunction<ClientSession, ClientListener<? super ClientSession>, SessionListener> teleports;

	/**
	 * Creates the sessions of a release.
	 *
	 * @param teleports creates the listener that answers the teleports of one session and reports them to the
	 * session's listener; the sessions add it before the listener that delivers the session's packets
	 */
	public NetworkClientSessions(
			@NotNull BiFunction<ClientSession, ClientListener<? super ClientSession>, SessionListener> teleports
	) {
		this.teleports = teleports;
	}

	@Override
	public @NotNull Class<ClientSession> type() {
		return ClientSession.class;
	}

	@Override
	public @NotNull ClientSession open(
			@NotNull ClientLogin login,
			@NotNull ClientListener<? super ClientSession> listener,
			@NotNull PacketTable<Packet> packets
	) {
		ClientProfile profile = login.getProfile();
		ClientCredentials credentials = login.getCredentials();
		ClientConnection connection = login.getConnection();
		InetSocketAddress source = connection.getSourceAddress() == null
				? null
				: new InetSocketAddress(connection.getSourceAddress(), ANY_PORT);
		ClientSession session = ClientNetworkSessionFactory.factory()
				.setAddress(connection.getHost(), connection.getPort())
				.setBindSocketAddress(source)
				.setProtocol(new MinecraftProtocol(new GameProfile(profile.getUniqueId(), profile.getName()),
						credentials == null ? null : credentials.getAccessToken()))
				.create();
		if (connection.getVirtualHost() != null)
			session.setFlag(MinecraftConstants.CLIENT_HOST, connection.getVirtualHost());
		if (credentials != null && credentials.getSessionServer() != null)
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY,
					new RedirectedSessionService(credentials.getSessionServer()));
		session.addListener(teleports.apply(session, listener));
		session.addListener(new ClientPacketListener(session, login, listener, packets));

		return session;
	}

	@Override
	public void connect(@NotNull ClientSession session) {
		session.connect(true);
	}
}
