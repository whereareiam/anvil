package me.whereareiam.anvil.protocol.worker.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.UUID;

/**
 * Decrypted authentication details of one account, which {@link me.whereareiam.anvil.protocol.worker.WorkerAuthentication}
 * resolves and the worker passes to its child JVM only over that JVM's private standard input.
 */
@Value
@Builder
public class AuthenticationSession {
	/**
	 * Minecraft username of the signed-in account.
	 */
	@NotNull String username;
	/**
	 * Profile UUID of the signed-in account.
	 */
	@NotNull UUID uuid;
	/**
	 * Access token of the signed-in account.
	 */
	@NotNull @ToString.Exclude String accessToken;
	/**
	 * Session server the login is reported to, or null for Mojang's.
	 */
	@Nullable URI sessionServer;
}
