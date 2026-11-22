package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ClientSessions;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_1.RedirectedSessionService;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;

/**
 * Sessions of the MCProtocolLib releases from 1.21.11 on: client sessions from the network session factory with
 * built-in profiles. The handshake announces a virtual host through the client-host flag, and the session binds the
 * source address as its bind socket address; the factory's {@code setBindAddress} is avoided because it replaces the
 * remote address instead. The session's {@link ClientProtocol} starts its configuration. The release hands most packets
 * to its session listeners on an executor of its own, after later packets were delivered and answered; each session
 * runs them on the channel's thread instead, right after {@code ReceivedFrames} delivered the packet, so what they and
 * MCProtocolLib answer leaves in the order the packets arrived, as the vanilla client answers.
 */
public final class NetworkClientSessions implements ClientSessions<ClientSession> {
	private static final int ANY_PORT = 0;
	/**
	 * Runs the session's listeners on the thread that delivers each packet, the channel's.
	 */
	private static final Executor ON_THE_CHANNEL = Runnable::run;

	private final ComponentTranslator text;

	/**
	 * Creates the sessions of a release.
	 *
	 * @param text translates the release's text components
	 */
	public NetworkClientSessions(@NotNull ComponentTranslator text) {
		this.text = text;
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
		ClientCredentials credentials = login.getCredentials();
		ClientConnection connection = login.getConnection();
		InetSocketAddress source = connection.getSourceAddress() == null
				? null
				: new InetSocketAddress(connection.getSourceAddress(), ANY_PORT);
		ClientSession session = ClientNetworkSessionFactory.factory()
				.setAddress(connection.getHost(), connection.getPort())
				.setBindSocketAddress(source)
				.setProtocol(new ClientProtocol(login))
				.setPacketHandlerExecutor(ON_THE_CHANNEL)
				.create();
		if (connection.getVirtualHost() != null)
			session.setFlag(MinecraftConstants.CLIENT_HOST, connection.getVirtualHost());
		if (credentials != null && credentials.getSessionServer() != null)
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY,
					new RedirectedSessionService(credentials.getSessionServer()));
		session.addListener(new ClientPacketListener(session, listener, packets, text));

		return session;
	}

	@Override
	public void connect(@NotNull ClientSession session) {
		session.connect(true);
	}
}
