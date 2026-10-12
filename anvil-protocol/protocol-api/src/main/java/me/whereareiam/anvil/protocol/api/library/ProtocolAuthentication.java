package me.whereareiam.anvil.protocol.api.library;

import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * The interactive account workflow of the protocol libraries a {@link ProtocolLibraryHost} runs. An account is one
 * Microsoft account: signing in stores its Microsoft token once, without choosing a library, and for each Minecraft
 * edition the installed libraries speak, one library of that edition checks what the edition's players can do with
 * the account and records it as the account's {@link AccountAccess access} for the edition. The record counts for
 * every installed library of that edition.
 *
 * <p>Credentials remain in the private account files and must never be returned to tooling, added to process
 * arguments or environment variables, or emitted through the output callback.</p>
 *
 * <pre>{@code
 * ProtocolAuthentication accounts = ProtocolLibraryRegistry.discover().authentication(directory).orElseThrow();
 * accounts.login("alice", System.out::println);
 * }</pre>
 */
public interface ProtocolAuthentication {
	/**
	 * Lists the locally stored accounts with what was recorded about them for each edition.
	 *
	 * @return account metadata without credential material, ordered by account ID
	 */
	@NotNull Collection<AuthenticationAccount> accounts();

	/**
	 * Signs a Microsoft account in interactively, stores it under the ID, replacing an account stored there, and
	 * lets one library of each edition the installed libraries speak check and record what the edition's players can
	 * do with the account. An edition whose services refuse the account does not fail the sign-in; the library's
	 * reason is recorded and reported.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing prompts and status, including one line per edition; never access or refresh tokens
	 * @throws IllegalArgumentException when the account ID is not a valid file name
	 * @throws IllegalStateException when the sign-in fails, is interrupted, or the account cannot be stored
	 */
	void login(@NotNull String accountId, @NotNull Consumer<String> output);

	/**
	 * Checks a stored account for one edition: an installed library of the edition derives a session from it, over
	 * the network, and the result is recorded for the edition, replacing what was recorded before.
	 *
	 * @param accountId owner-local account ID
	 * @param edition edition whose players would use the account
	 * @return the recorded access; {@link AccountAccess#unknown unknown}, with the reason, when the library could not
	 * tell or no installed library of the edition uses online accounts
	 * @throws IllegalArgumentException when the account ID is not a valid file name
	 * @throws IllegalStateException when no account is stored under the ID, or its file is invalid or cannot be written
	 */
	@NotNull AccountAccess check(@NotNull String accountId, @NotNull MinecraftEdition edition);

	/**
	 * Removes the named account and every session the libraries derived from it.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing status; never credentials
	 * @throws IllegalArgumentException when the account ID is not a valid file name
	 * @throws IllegalStateException when the account file cannot be removed
	 */
	void logout(@NotNull String accountId, @NotNull Consumer<String> output);
}
