package me.whereareiam.anvil.protocol.worker;

import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.worker.model.AuthenticationSession;
import org.jetbrains.annotations.NotNull;

/**
 * The account workflow of a library the native worker hosts: the login and logout of
 * {@link ProtocolAuthentication}, plus the resolution of a stored account into the session a worker player signs
 * in with. The worker delivers that session to its child JVM only over the child's private standard input.
 *
 * <pre>{@code
 * public Optional<WorkerAuthentication> authentication(Path accountsDirectory) {
 *     return Optional.of(new ExampleAuthentication(accountsDirectory));
 * }
 * }</pre>
 */
public interface WorkerAuthentication extends ProtocolAuthentication {
	/**
	 * Resolves a stored account into the session a worker player signs in with, refreshing it when needed. Never
	 * log the returned session's access token.
	 *
	 * @param accountId id of the stored account
	 * @return the account's current session
	 * @throws IllegalStateException when the account is unknown or cannot be refreshed
	 */
	@NotNull AuthenticationSession resolve(@NotNull String accountId);
}
