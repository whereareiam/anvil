package me.whereareiam.anvil.protocol.api.worker.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * The Microsoft account token a stored account holds: the OAuth token of one device-code sign-in, and the
 * Microsoft application it was issued to. The token can only be refreshed by that application, so the application
 * stays with the token. Every library derives its own game session from it; a library that refreshes the token
 * returns the new one, and the native worker stores it in place of the old one.
 *
 * <pre>{@code
 * MicrosoftToken token = MicrosoftToken.builder()
 *         .clientId("00000000402b5328")
 *         .scope("service::user.auth.xboxlive.com::MBI_SSL")
 *         .accessToken(accessToken)
 *         .refreshToken(refreshToken)
 *         .expiresAt(Instant.now().plusSeconds(86_400))
 *         .build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class MicrosoftToken {
	/**
	 * Client ID of the Microsoft application the token was issued to, such as an Xbox title ID.
	 */
	@NotNull String clientId;
	/**
	 * OAuth scope the token was issued for.
	 */
	@NotNull String scope;
	/**
	 * Microsoft access token.
	 */
	@ToString.Exclude
	@NotNull String accessToken;
	/**
	 * Microsoft refresh token, or null when the sign-in issued none and the account must sign in again once the
	 * access token expires.
	 */
	@ToString.Exclude
	@Nullable String refreshToken;
	/**
	 * When the access token expires.
	 */
	@NotNull Instant expiresAt;
}
