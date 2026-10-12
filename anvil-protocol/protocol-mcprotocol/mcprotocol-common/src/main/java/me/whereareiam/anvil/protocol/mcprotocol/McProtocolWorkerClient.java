package me.whereareiam.anvil.protocol.mcprotocol;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.worker.WorkerClient;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerConnection;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Drives the client segment's {@link McProtocolClient} for the worker's players: turns a player's options into the
 * client login, with the worker's own client settings, and the client's callbacks into session callbacks.
 */
@RequiredArgsConstructor
final class McProtocolWorkerClient implements WorkerClient {
	private static final String CLIENT_LOCALE = "en_us";
	private static final int VIEW_DISTANCE = 8;

	private final @NotNull McProtocolClient<Object> client;

	@Override
	public int protocolNumber() {
		return client.protocolNumber();
	}

	@Override
	public @NotNull Class<?> sessionType() {
		return client.sessionType();
	}

	@Override
	public @NotNull Object open(@NotNull WorkerPlayerOptions options, @NotNull SessionListener listener) {
		return client.open(login(options), new Listener(listener));
	}

	@Override
	public void connect(@NotNull Object session) {
		client.connect(session);
	}

	@Override
	public boolean connected(@NotNull Object session) {
		return client.connected(session);
	}

	@Override
	public void disconnect(@NotNull Object session, @NotNull String reason) {
		client.disconnect(session, reason);
	}

	/**
	 * Builds the client login from the resolved options, with the worker's own client settings.
	 */
	private ClientLogin login(WorkerPlayerOptions options) {
		WorkerProfile profile = options.getProfile();
		WorkerCredentials credentials = options.getCredentials();
		WorkerConnection connection = options.getConnection();
		return ClientLogin.builder()
				.profile(ClientProfile.builder().name(profile.getName()).uniqueId(profile.getUniqueId()).build())
				.credentials(credentials == null ? null : ClientCredentials.builder()
						.accessToken(credentials.getAccessToken())
						.sessionServer(credentials.getSessionServer())
						.build())
				.settings(ClientSettings.builder().locale(CLIENT_LOCALE).viewDistance(VIEW_DISTANCE).build())
				.connection(ClientConnection.builder()
						.host(connection.getHost())
						.port(connection.getPort())
						.virtualHost(connection.getVirtualHost())
						.sourceAddress(connection.getSourceAddress())
						.build())
				.build();
	}

	/**
	 * Forwards the client's callbacks of one session to the worker player's listener.
	 */
	@RequiredArgsConstructor
	private static final class Listener implements ClientListener<Object> {
		private final SessionListener listener;

		@Override
		public void loggedIn(@NotNull Object session, @NotNull ClientProfile granted) {
			listener.loggedIn(session, new PlayerProfile(granted.getName(), granted.getUniqueId()));
		}

		@Override
		public void teleported(@NotNull Object session, float yaw, float pitch) {
			listener.teleported(session, yaw, pitch);
		}

		@Override
		public void disconnected(@NotNull Object session, @NotNull DisconnectCause cause, @NotNull String reason) {
			listener.disconnected(session, cause, reason);
		}
	}
}
