package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.service.SessionService;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.codec.MinecraftCodec;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import org.jetbrains.annotations.NotNull;

/**
 * Client of MCProtocolLib 1.18.2: packetlib 2.1 TCP sessions, the codec and clientbound/serverbound packet
 * names, plain-string disconnect events and adventure 4.9 without its plain-text serializer.
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
		GameProfile profile = new GameProfile(login.getUniqueId(), login.getName());
		Session session = new TcpClientSession(login.getHost(), login.getPort(), new MinecraftProtocol(profile, login.getAccessToken()));
		if (login.getSessionServer() != null) {
			SessionService sessionService = new SessionService();
			sessionService.setBaseUri(login.getSessionServer() + "/");
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
		}
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
