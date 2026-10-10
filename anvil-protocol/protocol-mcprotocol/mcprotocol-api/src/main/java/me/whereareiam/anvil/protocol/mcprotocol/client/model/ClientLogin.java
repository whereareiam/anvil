package me.whereareiam.anvil.protocol.mcprotocol.client.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a {@link McProtocolClient} needs to log one player in: who logs in, with which client settings, and
 * where to.
 *
 * <pre>{@code
 * ClientLogin login = ClientLogin.builder()
 *         .profile(ClientProfile.builder().name("Alice").uniqueId(uuid).build())
 *         .settings(ClientSettings.builder().locale("en_us").viewDistance(8).build())
 *         .connection(ClientConnection.builder().host("127.0.0.1").port(25565).build())
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class ClientLogin {
	/**
	 * Profile sent in the login request.
	 */
	@NotNull ClientProfile profile;

	/**
	 * Online credentials, or null for an offline login.
	 */
	@Nullable ClientCredentials credentials;

	/**
	 * Client information sent after login.
	 */
	@NotNull ClientSettings settings;

	/**
	 * Game listener, announced host and source address of the connection.
	 */
	@NotNull ClientConnection connection;
}
