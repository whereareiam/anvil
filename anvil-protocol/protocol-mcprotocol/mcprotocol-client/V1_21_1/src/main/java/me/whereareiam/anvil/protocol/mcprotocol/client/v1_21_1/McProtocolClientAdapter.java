package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Client of MCProtocolLib 1.21.1: the GeyserMC packages with TCP sessions, built-in profiles and adventure
 * 4.15 without its plain-text serializer.
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
