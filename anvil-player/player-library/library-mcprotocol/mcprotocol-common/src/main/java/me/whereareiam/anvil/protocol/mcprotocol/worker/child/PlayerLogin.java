package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientConnection;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerProfile;
import org.jetbrains.annotations.NotNull;

/**
 * The client login of a worker player: the options the host resolved, with the player's language and the worker's
 * other client settings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PlayerLogin {
	private static final int VIEW_DISTANCE = 8;

	/**
	 * Builds the login a player's client opens each session with.
	 *
	 * @param options the player's resolved options
	 * @return the client login
	 */
	static @NotNull ClientLogin of(@NotNull WorkerPlayerOptions options) {
		WorkerProfile profile = options.getProfile();
		WorkerCredentials credentials = options.getCredentials();
		WorkerConnection connection = options.getConnection();
		return ClientLogin.builder()
				.profile(ClientProfile.builder().name(profile.getName()).uniqueId(profile.getUniqueId()).build())
				.credentials(credentials == null ? null : ClientCredentials.builder()
						.accessToken(credentials.getAccessToken())
						.sessionServer(credentials.getSessionServer())
						.build())
				.minecraftVersion(options.getMinecraftVersion())
				.settings(ClientSettings.builder().locale(options.getLanguage()).viewDistance(VIEW_DISTANCE).build())
				.connection(ClientConnection.builder()
						.host(connection.getHost())
						.port(connection.getPort())
						.virtualHost(connection.getVirtualHost())
						.sourceAddress(connection.getSourceAddress())
						.build())
				.build();
	}
}
