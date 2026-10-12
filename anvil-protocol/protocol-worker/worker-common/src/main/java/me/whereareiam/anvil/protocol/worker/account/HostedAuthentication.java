package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.ResolvedAccount;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The account workflow of one hosted library: the worker's account store around the library's online sign-in.
 * Login, logout and listing serve tooling as the library's {@link ProtocolAuthentication}; {@link #resolve} signs a
 * worker player in. Every operation refuses an account ID that another library stores before it contacts a service.
 *
 * <pre>{@code
 * HostedAuthentication accounts = new HostedAuthentication(library, signIn, accountsDirectory, newestVersion);
 * accounts.login("alice", System.out::println);
 * AuthenticationSession session = accounts.resolve("alice", MinecraftVersion.parse("1.21.11"));
 * }</pre>
 */
public final class HostedAuthentication implements ProtocolAuthentication {
	private final WorkerLibrary library;
	private final WorkerAuthentication authentication;
	private final AccountStore accounts;
	private final Supplier<MinecraftVersion> newestVersion;

	/**
	 * Creates the workflow of one library over an accounts directory.
	 *
	 * @param library the library that owns the accounts it stores
	 * @param authentication the library's online sign-in
	 * @param accountsDirectory directory every library stores its account files in
	 * @param newestVersion supplies the newest Minecraft version of the library's built-in releases, reported by a
	 * login
	 */
	public HostedAuthentication(
			@NotNull WorkerLibrary library,
			@NotNull WorkerAuthentication authentication,
			@NotNull Path accountsDirectory,
			@NotNull Supplier<MinecraftVersion> newestVersion
	) {
		this.library = library;
		this.authentication = authentication;
		this.accounts = new AccountStore(library, accountsDirectory);
		this.newestVersion = newestVersion;
	}

	/**
	 * Lists the stored accounts this library owns.
	 *
	 * @return account metadata without credentials, ordered by account ID
	 */
	@Override
	public @NotNull Collection<AuthenticationAccount> accounts() {
		try {
			return accounts.accounts();
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not list the authentication accounts of " + library.getName(), exception);
		}
	}

	/**
	 * Signs an account in through the library and stores it.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing prompts and status; never credentials
	 * @throws IllegalStateException when another library stores the ID, its file is unreadable, or the sign-in fails
	 */
	@Override
	public void login(@NotNull String accountId, @NotNull Consumer<String> output) {
		try {
			accounts.requireAvailable(accountId);
			AccountRecord account = authentication.login(accountId, newestVersion.get(), output);
			accounts.write(account.toBuilder().accountId(accountId).build());
			output.accept("Authenticated " + account.getUsername() + " (" + account.getUniqueId() + ") as account '" + accountId + "'.");
		} catch (IOException exception) {
			throw new IllegalStateException("Could not store authentication account '" + accountId + "'", exception);
		}
	}

	/**
	 * Removes a stored account of this library.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing status
	 * @throws IllegalStateException when another library stores the ID or its file is unreadable
	 */
	@Override
	public void logout(@NotNull String accountId, @NotNull Consumer<String> output) {
		try {
			if (accounts.delete(accountId)) {
				output.accept("Removed authentication account '" + accountId + "'.");
				return;
			}

			output.accept("Authentication account '" + accountId + "' did not exist.");
		} catch (IOException exception) {
			throw new IllegalStateException("Could not remove authentication account '" + accountId + "'", exception);
		}
	}

	/**
	 * Signs a stored account in for a player and stores the refreshed record. Never log the returned token.
	 *
	 * @param accountId owner-local account ID
	 * @param version Minecraft version of the player that signs in
	 * @return the session the player signs in with
	 * @throws IllegalStateException when the account is unknown, another library stores it, or it cannot be refreshed
	 */
	public @NotNull AuthenticationSession resolve(@NotNull String accountId, @NotNull MinecraftVersion version) {
		try {
			ResolvedAccount resolved = authentication.resolve(accounts.read(accountId), version);
			accounts.write(resolved.getAccount().toBuilder().accountId(accountId).build());
			return resolved.getSession();
		} catch (IOException exception) {
			throw new IllegalStateException("Could not refresh authentication account '" + accountId + "'", exception);
		}
	}
}
