package me.whereareiam.anvil.protocol.api.worker;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.ResolvedAccount;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * The online sign-in of a library the native worker hosts. The worker owns the account store: it writes the record
 * a login returns into the account's file, labelled with the library's id, reads it back to sign a player in, saves
 * the refreshed record, removes it on logout, and refuses an account another library stores. The library only talks
 * to its online services and never touches an account file.
 *
 * <p>The worker exposes the workflow as the library's {@link ProtocolAuthentication}, and delivers a resolved
 * session to its child JVM only over the child's private standard input.</p>
 *
 * <pre>{@code
 * public Optional<WorkerAuthentication> authentication() {
 *     return Optional.of(new ExampleAuthentication());
 * }
 * }</pre>
 */
public interface WorkerAuthentication {
	/**
	 * Signs an account in interactively, such as through a device-code flow, and returns what the worker stores of
	 * it. The worker has already made sure that no other library stores the account ID.
	 *
	 * @param accountId owner-local account ID the record is stored under
	 * @param version newest Minecraft version of the library's built-in releases, for services that want a game
	 * version
	 * @param output user-facing prompts and status; never access or refresh tokens
	 * @return the signed-in account with its credentials
	 * @throws IllegalStateException when the sign-in fails or is interrupted
	 */
	@NotNull AccountRecord login(@NotNull String accountId, @NotNull MinecraftVersion version, @NotNull Consumer<String> output);

	/**
	 * Resolves a stored account into the session a player of the given version signs in with, refreshing the
	 * credentials when needed. The worker stores the returned record in place of the given one. Never log the
	 * returned session's access token or credentials.
	 *
	 * @param account the account the library stored, read back by the worker
	 * @param version Minecraft version of the player that signs in, for services that want a game version
	 * @return the session to sign in with and the record to keep
	 * @throws IllegalStateException when the stored credentials are invalid or cannot be refreshed; the message
	 * never quotes them
	 */
	@NotNull ResolvedAccount resolve(@NotNull AccountRecord account, @NotNull MinecraftVersion version);
}
