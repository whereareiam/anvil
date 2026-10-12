package me.whereareiam.anvil.protocol.api.worker.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.UUID;

/**
 * Decrypted authentication details of one account, which {@link me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication}
 * resolves and the worker passes to its child JVM only over that JVM's private standard input.
 *
 * <p>The worker reads the profile and nothing else: the access token and the library credentials reach the
 * library's own {@link me.whereareiam.anvil.protocol.api.worker.WorkerClient} unchanged, as
 * {@link me.whereareiam.anvil.protocol.api.worker.model.message.WorkerCredentials}.</p>
 *
 * <pre>{@code
 * AuthenticationSession session = AuthenticationSession.builder()
 *         .username("Alice")
 *         .uuid(uuid)
 *         .accessToken(token)
 *         .libraryCredentials(keysAndChain)
 *         .build();
 * }</pre>
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
	 * Session server the login is reported to, or null for the library's default.
	 */
	@Nullable URI sessionServer;
	/**
	 * Further secrets the library's client signs in with besides the access token, such as a key pair and a
	 * certificate chain, in a text form the library defines; null when the access token suffices. The worker never
	 * reads them.
	 */
	@Nullable @ToString.Exclude String libraryCredentials;
}
