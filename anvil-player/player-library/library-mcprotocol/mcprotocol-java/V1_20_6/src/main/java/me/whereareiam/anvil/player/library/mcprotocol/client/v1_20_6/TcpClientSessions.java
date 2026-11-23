package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.service.SessionService;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundGameProfilePacket;
import org.jetbrains.annotations.NotNull;

/**
 * Sessions of the MCProtocolLib 1.20.6 release: TCP sessions whose profiles and session service still come from
 * mcauthlib, which MCProtocolLib 1.21 replaced with its own. Each session is a {@link FramedClientSession}, which hands
 * the packets it receives over in the order they arrive. A session binds the source address through its bind-address
 * constructor; the handshake always announces the session host, so {@link ClientPacketListener} rewrites the handshake
 * for a virtual host. The session's {@link ClientProtocol} starts its configuration, and {@link KeepAliveAnswers}
 * answer its keep-alives in both phases.
 */
final class TcpClientSessions implements ClientSessions<Session> {
	/**
	 * Separates the session server's address from the paths the session service appends to it.
	 */
	private static final String PATH_SEPARATOR = "/";

	private final ComponentTranslator text;

	/**
	 * Creates the sessions of the release.
	 *
	 * @param text translates the release's text components
	 */
	TcpClientSessions(@NotNull ComponentTranslator text) {
		this.text = text;
	}

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
		ClientCredentials credentials = login.credentials();
		ClientConnection connection = login.connection();
		ClientProtocol protocol = new ClientProtocol(login);
		Session session = connection.sourceAddress() == null
				? new FramedClientSession(connection.host(), connection.port(), protocol, listener, packets)
				: new FramedClientSession(connection.host(), connection.port(), connection.sourceAddress(),
						protocol, listener, packets);
		if (credentials != null && credentials.sessionServer() != null) {
			SessionService sessionService = new SessionService();
			sessionService.setBaseUri(credentials.sessionServer() + PATH_SEPARATOR);
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
		}
		KeepAliveAnswers.install(session);
		session.addListener(new ClientPacketListener(login, listener, TcpClientSessions::granted, text));

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
