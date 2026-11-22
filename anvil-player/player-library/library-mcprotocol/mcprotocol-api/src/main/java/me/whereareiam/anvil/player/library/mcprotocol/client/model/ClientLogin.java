package me.whereareiam.anvil.player.library.mcprotocol.client.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.player.library.mcprotocol.client.McProtocolClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a {@link McProtocolClient} needs to log one player in: who logs in, as which version of the game, with
 * which client settings, and where to.
 *
 * <pre>{@code
 * ClientLogin login = ClientLogin.builder()
 *         .profile(ClientProfile.builder().name("Alice").uniqueId(uuid).build())
 *         .minecraftVersion("1.20.5")
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
	 * The Minecraft version the player's client is, such as {@code 1.20.5}: one of the versions that speak the
	 * release's protocol, which the client names where the protocol asks for its own version, as in the vanilla data
	 * pack it tells the server it knows.
	 */
	@NotNull String minecraftVersion;

	/**
	 * Client information sent after login.
	 */
	@NotNull ClientSettings settings;

	/**
	 * Game listener, announced host and source address of the connection.
	 */
	@NotNull ClientConnection connection;
}
