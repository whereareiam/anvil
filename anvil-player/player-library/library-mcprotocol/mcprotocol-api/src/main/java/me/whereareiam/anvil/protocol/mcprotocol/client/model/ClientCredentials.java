package me.whereareiam.anvil.protocol.mcprotocol.client.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * Online credentials of a client: the access token it reports its login with and the session server it reports
 * to. An offline login has none.
 *
 * <pre>{@code
 * ClientCredentials credentials = ClientCredentials.builder()
 *         .accessToken(token)
 *         .sessionServer(URI.create("http://127.0.0.1:25580/session/minecraft"))
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class ClientCredentials {
	/**
	 * Access token the session server accepts for the profile. Never logged.
	 */
	@ToString.Exclude
	@NotNull String accessToken;

	/**
	 * Session server the login is reported to instead of Mojang's, or null for Mojang's.
	 */
	@Nullable URI sessionServer;
}
