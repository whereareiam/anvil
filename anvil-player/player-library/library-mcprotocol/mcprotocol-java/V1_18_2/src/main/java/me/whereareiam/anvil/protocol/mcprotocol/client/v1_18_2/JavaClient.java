package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.codec.MinecraftCodec;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client of the MCProtocolLib releases from 1.18.2 until 1.19.4, which every adapter of this segment and of the
 * segments extending it completes with its release's disconnect reasons and packet table: packetlib TCP sessions,
 * mcauthlib's profiles and session service, the codec and clientbound/serverbound packet names. The session binds the
 * source address through packetlib's bind-address constructor; the handshake always announces the session host, so
 * {@link ClientPacketListener} rewrites the handshake for a virtual host. Each session decodes through a table of its
 * own, since a translation may remember what earlier packets of the session said, such as the types of its entities.
 */
public class JavaClient implements McProtocolClient<Session> {
	private static final int ANY_PORT = 0;
	/**
	 * Separates the session server's address from the paths the session service appends to it.
	 */
	private static final String PATH_SEPARATOR = "/";

	private final Function<DisconnectedEvent, String> reasons;
	private final Supplier<PacketTable<Packet>> tables;
	private final PacketTable<Packet> packets;

	/**
	 * Creates the client of a release.
	 *
	 * @param reasons flattens the reason of a closed connection to plain text, or to an empty text when it has none
	 * @param tables creates a new packet table of the release
	 */
	protected JavaClient(
			@NotNull Function<DisconnectedEvent, String> reasons,
			@NotNull Supplier<PacketTable<Packet>> tables
	) {
		this.reasons = reasons;
		this.tables = tables;
		this.packets = tables.get();
	}

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
				: new TcpClientSession(connection.getHost(), connection.getPort(), connection.getSourceAddress(),
						ANY_PORT, protocol);
		if (credentials != null && credentials.getSessionServer() != null) {
			SessionService sessionService = new SessionService();
			sessionService.setBaseUri(credentials.getSessionServer() + PATH_SEPARATOR);
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
		}
		session.addListener(new ClientPacketListener(login, listener, tables.get(), reasons));

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

	@Override
	public void send(@NotNull Session session, @NotNull ServerboundPacket packet) {
		session.send(packets.encode(packet));
	}

	@Override
	public boolean supports(@NotNull Class<? extends JavaPacket> type) {
		return packets.supports(type);
	}
}
