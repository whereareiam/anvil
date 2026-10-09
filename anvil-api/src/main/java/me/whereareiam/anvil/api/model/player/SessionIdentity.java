package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.UUID;

/**
 * An online identity that a session server chosen by the scenario verifies, instead of a stored account
 * that Mojang's session server verifies. Use it with a local stand-in for the session server, so an
 * online-mode login can be tested without a real Minecraft account.
 *
 * <p>The session server is the base address whose {@code join} endpoint the client reports its login to,
 * for example {@code http://127.0.0.1:25580/session/minecraft}. The process the player joins must verify
 * logins against the same server; see {@code MinecraftProcess.getSessionServer()}.</p>
 *
 * <pre>{@code
 * SessionIdentity identity = SessionIdentity.builder()
 *         .username("Alice")
 *         .uniqueId(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"))
 *         .accessToken("alice-token")
 *         .sessionServer(URI.create("http://127.0.0.1:25580/session/minecraft"))
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class SessionIdentity {
	/**
	 * Profile name the player logs in with.
	 */
	@NotNull String username;

	/**
	 * Profile identity the session server knows the username by.
	 */
	@NotNull UUID uniqueId;

	/**
	 * Token the session server accepts for this profile. It is never a real Minecraft access token.
	 */
	@ToString.Exclude
	@NotNull String accessToken;

	/**
	 * Base address of the session server, without a trailing slash.
	 */
	@NotNull URI sessionServer;
}
