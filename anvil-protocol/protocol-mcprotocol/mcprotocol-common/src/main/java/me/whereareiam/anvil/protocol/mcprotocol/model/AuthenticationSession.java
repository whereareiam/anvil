package me.whereareiam.anvil.protocol.mcprotocol.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.UUID;

/**
 * Decrypted authentication details passed to a worker only over its private standard input.
 */
@Value
@Builder
public class AuthenticationSession {
	@NotNull String username;
	@NotNull UUID uuid;
	@NotNull @ToString.Exclude String accessToken;
	/**
	 * Session server the login is reported to, or null for Mojang's.
	 */
	@Nullable URI sessionServer;
}
