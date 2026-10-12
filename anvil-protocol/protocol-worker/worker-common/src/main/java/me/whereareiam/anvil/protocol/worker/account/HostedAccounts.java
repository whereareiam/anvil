package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import me.whereareiam.anvil.protocol.worker.account.AccountStore.LibraryEntry;
import me.whereareiam.anvil.protocol.worker.account.AccountStore.StoredAccount;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The accounts of the libraries the native worker hosts. An account is one Microsoft account: {@link #login} signs it
 * in once and then lets each library check what it can do with it, and {@link #resolve} derives the session one
 * library's player signs in with. Every derivation records the library's result and stores the Microsoft token as the
 * library left it.
 *
 * <pre>{@code
 * HostedAccounts accounts = new HostedAccounts(
 *         List.of(new HostedAccounts.Library(library, derivation, newestVersion)),
 *         accountsDirectory
 * );
 * accounts.login("alice", System.out::println);
 * AuthenticationSession session = accounts.resolve("mcprotocol", "alice", MinecraftVersion.parse("1.21.11"));
 * }</pre>
 */
public final class HostedAccounts implements ProtocolAuthentication {
	private final Map<String, Library> libraries = new LinkedHashMap<>();
	private final AccountStore accounts;
	private final BiFunction<String, Consumer<String>, MicrosoftToken> signIn;

	/**
	 * Creates the accounts of the given libraries over an accounts directory, signing in through Microsoft.
	 *
	 * @param libraries the hosted libraries that use online accounts
	 * @param accountsDirectory directory of the account files
	 */
	public HostedAccounts(@NotNull Collection<Library> libraries, @NotNull Path accountsDirectory) {
		this(libraries, accountsDirectory, new MicrosoftSignIn()::signIn);
	}

	HostedAccounts(
			@NotNull Collection<Library> libraries,
			@NotNull Path accountsDirectory,
			@NotNull BiFunction<String, Consumer<String>, MicrosoftToken> signIn
	) {
		for (Library library : libraries) this.libraries.put(library.library().getId(), library);
		this.accounts = new AccountStore(accountsDirectory);
		this.signIn = signIn;
	}

	/**
	 * Lists the stored accounts with what each library recorded about them.
	 *
	 * @return account metadata without credentials, ordered by account ID
	 */
	@Override
	public @NotNull Collection<AuthenticationAccount> accounts() {
		try {
			return accounts.accounts();
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not list the stored accounts", exception);
		}
	}

	/**
	 * Signs a Microsoft account in, stores it in place of any account stored under the ID, and lets every library
	 * check the account with the newest version of its built-in releases. Prints each library's result.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing prompts and status; never credentials
	 * @throws IllegalArgumentException when the ID is not a valid file name
	 * @throws IllegalStateException when the sign-in fails or the account cannot be stored
	 */
	@Override
	public void login(@NotNull String accountId, @NotNull Consumer<String> output) {
		accounts.accountFile(accountId);
		MicrosoftToken token = signIn.apply(accountId, output);
		synchronized (accounts.lock(accountId)) {
			try {
				StoredAccount account = StoredAccount.builder().accountId(accountId).token(token).build();
				accounts.write(account);
				output.accept("Signed in account '" + accountId + "'.");
				for (Library library : libraries.values()) {
					account = check(account, library);
					accounts.write(account);
					output.accept(account.getLibraries().get(library.library().getId()).access().describe());
				}
			} catch (IOException exception) {
				throw new IllegalStateException("Could not store account '" + accountId + "'", exception);
			}
		}
	}

	/**
	 * Removes a stored account.
	 *
	 * @param accountId owner-local account ID
	 * @param output user-facing status
	 * @throws IllegalArgumentException when the ID is not a valid file name
	 */
	@Override
	public void logout(@NotNull String accountId, @NotNull Consumer<String> output) {
		synchronized (accounts.lock(accountId)) {
			try {
				if (accounts.delete(accountId)) {
					output.accept("Removed account '" + accountId + "'.");
					return;
				}

				output.accept("Account '" + accountId + "' did not exist.");
			} catch (IOException exception) {
				throw new IllegalStateException("Could not remove account '" + accountId + "'", exception);
			}
		}
	}

	/**
	 * Derives the session a player of one library signs in with from a stored account, and records the library's
	 * result. Never log the returned token.
	 *
	 * @param libraryId library of the player
	 * @param accountId owner-local account ID
	 * @param version Minecraft version of the player
	 * @return the session the player signs in with
	 * @throws IllegalArgumentException when the library is not one of these accounts' libraries
	 * @throws IllegalStateException when the account is unknown or invalid, when the library refuses it, with the
	 * library's reason, or when the library cannot derive a session
	 */
	public @NotNull AuthenticationSession resolve(@NotNull String libraryId, @NotNull String accountId, @NotNull MinecraftVersion version) {
		Library library = libraries.get(libraryId);
		if (library == null)
			throw new IllegalArgumentException("Protocol library '" + libraryId + "' derives no sessions from stored accounts");

		synchronized (accounts.lock(accountId)) {
			try {
				StoredAccount account = accounts.read(accountId);
				DerivedSession derived = derive(account, library, version);
				accounts.write(record(account, library, derived));
				if (!derived.isAvailable()) throw new IllegalStateException(derived.getRefusal());

				return derived.getSession();
			} catch (IOException exception) {
				throw new IllegalStateException("Could not refresh account '" + accountId + "'", exception);
			}
		}
	}

	/**
	 * Lets a library check a freshly signed-in account. A library that cannot tell records unknown availability with
	 * its failure, which does not fail the sign-in.
	 */
	private StoredAccount check(StoredAccount account, Library library) {
		try {
			return record(account, library, derive(account, library, library.newestVersion().get()));
		} catch (RuntimeException failure) {
			String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
			return account.toBuilder()
					.library(library.library().getId(), new LibraryEntry(AccountAccess.unknown(library.library().getId(), reason),
							account.cachedSession(library.library().getId())))
					.build();
		}
	}

	private static DerivedSession derive(StoredAccount account, Library library, MinecraftVersion version) {
		return library.authentication().derive(AccountCredentials.builder()
				.accountId(account.getAccountId())
				.token(account.getToken())
				.cachedSession(account.cachedSession(library.library().getId()))
				.build(), version);
	}

	private static StoredAccount record(StoredAccount account, Library library, DerivedSession derived) {
		String libraryId = library.library().getId();
		AuthenticationSession session = derived.getSession();
		LibraryEntry entry = session == null
				? new LibraryEntry(AccountAccess.unavailable(libraryId, derived.getRefusal()), null)
				: new LibraryEntry(AccountAccess.available(libraryId, session.getUsername(), session.getUuid()), derived.getCachedSession());

		return account.toBuilder()
				.token(derived.getToken())
				.library(libraryId, entry)
				.build();
	}

	/**
	 * One hosted library that derives sessions from stored accounts.
	 *
	 * @param library the library's description
	 * @param authentication how the library derives its session
	 * @param newestVersion supplies the newest Minecraft version of the library's built-in releases, which a
	 * freshly signed-in account is checked with
	 */
	public record Library(
			@NotNull WorkerLibrary library,
			@NotNull WorkerAuthentication authentication,
			@NotNull Supplier<MinecraftVersion> newestVersion
	) { }
}
