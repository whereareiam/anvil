package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Client of MCProtocolLib 1.21.11: client sessions from the network session factory, built-in profiles,
 * particle settings, the player-loaded acknowledgement after each teleport and adventure 4.25 without its
 * plain-text serializer.
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
		GameProfile profile = new GameProfile(login.getUniqueId(), login.getName());
		ClientSession session = ClientNetworkSessionFactory.factory()
				.setAddress(login.getHost(), login.getPort())
				.setProtocol(new MinecraftProtocol(profile, login.getAccessToken()))
				.create();
		if (login.getSessionServer() != null)
			session.setFlag(MinecraftConstants.SESSION_SERVICE_KEY, new RedirectedSessionService(login.getSessionServer()));
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
