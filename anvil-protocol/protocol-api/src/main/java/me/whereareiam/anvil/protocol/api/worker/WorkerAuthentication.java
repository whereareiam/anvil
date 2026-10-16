package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import org.jetbrains.annotations.NotNull;

/**
 * How a library the native worker hosts derives its own game session from a stored account. An account is one
 * Microsoft account: the worker signs it in once, without a library, and stores its Microsoft token. The library has
 * no sign-in of its own; it turns the token into what its players sign in with, such as a game access token, and
 * tells the worker when the services of its edition refuse the account.
 *
 * <p>The worker owns the account files and the shared {@link ProtocolAuthentication}. Right after a sign-in it asks
 * one library of each edition the hosted libraries speak to derive a session, and records the result for that
 * edition, where it counts for every library of the edition. It asks again for each player that uses the account,
 * and when a pool checks an account of unknown availability; it then stores the refreshed Microsoft token, the
 * library's cached session and the result for the edition, and delivers the session to the player's worker JVM only
 * over that JVM's private standard input.</p>
 *
 * <pre>{@code
 * public Optional<WorkerAuthentication> authentication() {
 *     return Optional.of(new ExampleAuthentication());
 * }
 * }</pre>
 */
public interface WorkerAuthentication {
	/**
	 * Derives the session a player of the given version signs in with from the account's Microsoft token, reusing
	 * the library's cached session while it is valid. Never log the returned session's tokens or credentials.
	 *
	 * <p>When the services of the edition refuse the account, such as one that owns no profile of that edition of
	 * the game, return {@link DerivedSession#unavailable} with a reason that names the account. Throw only when the
	 * library cannot tell, for example because a service cannot be reached.</p>
	 *
	 * @param account the account's Microsoft token and this library's cached session
	 * @param version Minecraft version of the player that signs in, or the newest version of the library's built-in
	 * releases of the checked edition when the worker checks an account; for services that want a game version
	 * @return the session to sign in with, or the library's refusal, with the token as the library left it
	 * @throws IllegalStateException when the library cannot tell whether the account is usable, such as when its
	 * cached session is invalid or a service fails; the message names the account and never quotes credentials
	 */
	@NotNull DerivedSession derive(@NotNull AccountCredentials account, @NotNull MinecraftVersion version);
}
