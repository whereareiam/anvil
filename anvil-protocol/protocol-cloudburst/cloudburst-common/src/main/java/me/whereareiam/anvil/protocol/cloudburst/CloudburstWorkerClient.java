package me.whereareiam.anvil.protocol.cloudburst;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.worker.WorkerClient;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerConnection;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientIdentity;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientListener;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.identity.LoginIdentity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drives the client segment's {@link CloudburstClient} for the worker's players: turns a player's options into a
 * Bedrock login, offline with a self-signed identity or online with the Xbox identity its account resolved, and the
 * client's callbacks into session callbacks.
 */
@RequiredArgsConstructor
final class CloudburstWorkerClient implements WorkerClient {
	private final @NotNull CloudburstClient<CloudburstSession<?, ?>> client;

	@Override
	public int protocolNumber() {
		return client.protocolNumber();
	}

	@Override
	public @NotNull Class<?> sessionType() {
		return client.sessionType();
	}

	/**
	 * Opens a session of the player's login.
	 *
	 * @throws IllegalArgumentException when the connection names a virtual host, which Bedrock has no handshake to
	 * announce, when the credentials carry no Xbox identity, or when the release has no codec for the protocol
	 */
	@Override
	public @NotNull Object open(@NotNull WorkerPlayerOptions options, @NotNull SessionListener listener) {
		return client.open(login(options), new Listener(listener));
	}

	@Override
	public void connect(@NotNull Object session) {
		client.connect((CloudburstSession<?, ?>) session);
	}

	@Override
	public boolean connected(@NotNull Object session) {
		return client.connected((CloudburstSession<?, ?>) session);
	}

	@Override
	public void disconnect(@NotNull Object session, @NotNull String reason) {
		client.disconnect((CloudburstSession<?, ?>) session, reason);
	}

	private ClientLogin login(WorkerPlayerOptions options) {
		WorkerConnection connection = options.getConnection();
		if (connection.getVirtualHost() != null)
			throw new IllegalArgumentException("A Bedrock connection has no handshake host, so it cannot announce the virtual host '"
					+ connection.getVirtualHost() + "'; connect without one");

		WorkerProfile profile = options.getProfile();
		ClientProfile claimed = ClientProfile.builder().name(profile.getName()).uniqueId(profile.getUniqueId()).build();
		return ClientLogin.builder()
				.profile(claimed)
				.identity(identity(claimed, options.getCredentials()))
				.connection(ClientConnection.builder()
						.host(connection.getHost())
						.port(connection.getPort())
						.sourceAddress(connection.getSourceAddress())
						.protocol(connection.getProtocol())
						.build())
				.build();
	}

	private static ClientIdentity identity(ClientProfile profile, @Nullable WorkerCredentials credentials) {
		if (credentials == null) return LoginIdentity.offline(profile);

		String xbox = credentials.getLibraryCredentials();
		if (xbox == null)
			throw new IllegalArgumentException("A Bedrock player signs in online only with a stored Xbox account; "
					+ "a session identity without one cannot sign a Bedrock login");

		return LoginIdentity.xbox(profile, credentials.getAccessToken(), xbox);
	}

	/**
	 * Forwards the client's callbacks of one session to the worker player's listener.
	 */
	@RequiredArgsConstructor
	private static final class Listener implements ClientListener<CloudburstSession<?, ?>> {
		private final SessionListener listener;

		@Override
		public void loggedIn(@NotNull CloudburstSession<?, ?> session, @NotNull ClientProfile granted) {
			listener.loggedIn(session, new PlayerProfile(granted.getName(), granted.getUniqueId()));
		}

		@Override
		public void teleported(@NotNull CloudburstSession<?, ?> session, float yaw, float pitch) {
			listener.teleported(session, yaw, pitch);
		}

		@Override
		public void disconnected(@NotNull CloudburstSession<?, ?> session, @NotNull DisconnectCause cause, @NotNull String reason) {
			listener.disconnected(session, cause, reason);
		}
	}
}
