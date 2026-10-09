package me.whereareiam.anvil.protocol.mcprotocol.client.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Everything a {@link McProtocolClient} needs to log one player in: profile, game endpoint, optional online
 * credentials and the client information sent after login.
 *
 * <pre>{@code
 * ClientLogin login = ClientLogin.builder()
 *         .name("Alice")
 *         .uniqueId(uuid)
 *         .host("127.0.0.1")
 *         .port(25565)
 *         .locale("en_us")
 *         .viewDistance(8)
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class ClientLogin {
	/**
	 * Profile name sent in the login request.
	 */
	@NotNull String name;
	/**
	 * Profile identity sent in the login request.
	 */
	@NotNull UUID uniqueId;
	/**
	 * Host name or address of the game listener.
	 */
	@NotNull String host;
	/**
	 * Port of the game listener.
	 */
	int port;
	/**
	 * Minecraft access token for online-mode servers, or null for offline login. Never logged.
	 */
	@ToString.Exclude
	@Nullable String accessToken;
	/**
	 * Locale reported in the client information, such as {@code en_us}.
	 */
	@NotNull String locale;
	/**
	 * View distance in chunks reported in the client information.
	 */
	int viewDistance;
}
