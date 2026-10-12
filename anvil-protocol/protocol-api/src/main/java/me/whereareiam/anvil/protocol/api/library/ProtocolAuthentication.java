package me.whereareiam.anvil.protocol.api.library;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * The interactive account workflow of the protocol libraries a {@link ProtocolLibraryHost} runs. An account is one
 * Microsoft account: signing in stores its Microsoft token once, without choosing a library, and every library
 * that can use online accounts then checks what it can do with the account and records it as the account's
 * {@link me.whereareiam.anvil.api.model.player.AccountAccess access}.
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
	 * Lists the locally stored accounts with what each library recorded about them.
	 *
	 * @return account metadata without credential material, ordered by account ID
	 */
	@NotNull Collection<AuthenticationAccount> accounts();

	/**
	 * Signs a Microsoft account in interactively, stores it under the ID, replacing an account stored there, and
	 * lets every library check and record what it can do with the account. A library whose services refuse the
	 * account does not fail the sign-in; its reason is recorded and reported.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing prompts and status, including each library's result; never access or refresh
	 * tokens
	 * @throws IllegalArgumentException when the account ID is not a valid file name
	 * @throws IllegalStateException when the sign-in fails, is interrupted, or the account cannot be stored
	 */
	void login(@NotNull String accountId, @NotNull Consumer<String> output);

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
