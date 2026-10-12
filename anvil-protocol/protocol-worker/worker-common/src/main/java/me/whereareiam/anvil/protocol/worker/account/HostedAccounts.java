package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import me.whereareiam.anvil.protocol.worker.account.AccountStore.StoredAccount;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The accounts of the libraries the native worker hosts. An account is one Microsoft account: {@link #login} signs it
 * in once and then lets one library of each Minecraft edition the libraries speak check what the edition's players can
 * do with it, {@link #check} checks it again for one edition, and {@link #resolve} derives the session one library's
 * player signs in with. Every derivation records the result for the edition, where it counts for every library of the
 * edition, and stores the Microsoft token as the library left it.
 *
 * <pre>{@code
 * HostedAccounts accounts = new HostedAccounts(
 *         List.of(new HostedAccounts.Library(library, derivation, releases)),
 *         accountsDirectory
 * );
 * accounts.login("alice", System.out::println);
 * AuthenticationSession session = accounts.resolve("examplelib", edition, "alice", MinecraftVersion.parse("1.21.11"));
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
		this.accounts = new AccountStore(accountsDirectory, this::soleEdition);
		this.signIn = signIn;
	}

	/**
	 * Lists the stored accounts with what was recorded about them for each edition.
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
	 * Signs a Microsoft account in, stores it in place of any account stored under the ID, and lets the first library
	 * of each edition check the account with the newest version of its built-in releases of the edition. Prints the
	 * result of each edition.
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
				StoredAccount account = StoredAccount.of(accountId, token);
				accounts.write(account);
				output.accept("Signed in account '" + accountId + "'.");
				for (Map.Entry<MinecraftEdition, Library> checker : checkers().entrySet()) {
					account = check(account, checker.getValue(), checker.getKey());
					accounts.write(account);
					output.accept(account.access(checker.getKey()).describe());
				}
			} catch (IOException exception) {
				throw new IllegalStateException("Could not store account '" + accountId + "'", exception);
			}
		}
	}

	/**
	 * Checks a stored account for one edition with the first library of the edition, and records the result.
	 *
	 * @param accountId owner-local account ID
	 * @param edition edition whose players would use the account
	 * @return the recorded access, or unknown access when no hosted library of the edition uses online accounts
	 * @throws IllegalArgumentException when the ID is not a valid file name
	 * @throws IllegalStateException when the account is not stored, its file is invalid, or it cannot be stored
	 */
	@Override
	public @NotNull AccountAccess check(@NotNull String accountId, @NotNull MinecraftEdition edition) {
		Library library = checkers().get(edition);
		if (library == null)
			return AccountAccess.unknown(edition, "no installed protocol library of edition '" + edition + "' plays online");

		synchronized (accounts.lock(accountId)) {
			try {
				StoredAccount account = check(accounts.read(accountId), library, edition);
				accounts.write(account);

				return account.access(edition);
			} catch (IOException exception) {
				throw new IllegalStateException("Could not check account '" + accountId + "'", exception);
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
	 * Derives the session a player of one library signs in with from a stored account, and records the result for
	 * the edition the player speaks. Never log the returned token.
	 *
	 * @param libraryId library of the player
	 * @param edition edition of the release the player speaks
	 * @param accountId owner-local account ID
	 * @param version Minecraft version of the player
	 * @return the session the player signs in with
	 * @throws IllegalArgumentException when the library is not one of these accounts' libraries
	 * @throws IllegalStateException when the account is unknown or invalid, when the edition's services refuse it,
	 * with the library's reason, or when the library cannot derive a session
	 */
	public @NotNull AuthenticationSession resolve(
			@NotNull String libraryId,
			@NotNull MinecraftEdition edition,
			@NotNull String accountId,
			@NotNull MinecraftVersion version
	) {
		Library library = libraries.get(libraryId);
		if (library == null)
			throw new IllegalArgumentException("Protocol library '" + libraryId + "' derives no sessions from stored accounts");

		synchronized (accounts.lock(accountId)) {
			try {
				StoredAccount account = accounts.read(accountId);
				DerivedSession derived = derive(account, library, version);
				accounts.write(record(account, library, edition, derived));
				if (!derived.isAvailable()) throw new IllegalStateException(derived.getRefusal());

				return derived.getSession();
			} catch (IOException exception) {
				throw new IllegalStateException("Could not refresh account '" + accountId + "'", exception);
			}
		}
	}

	/**
	 * Returns the library that checks each edition: the first library, in installation order, whose releases of the
	 * edition sign players in online.
	 */
	private Map<MinecraftEdition, Library> checkers() {
		Map<MinecraftEdition, Library> checkers = new LinkedHashMap<>();
		for (Library library : libraries.values())
			for (MinecraftEdition edition : library.editions()) checkers.putIfAbsent(edition, library);

		return checkers;
	}

	/**
	 * Maps a library to the one edition its online releases speak, which the per-library record of an account file of
	 * an earlier schema becomes the record of.
	 */
	private Optional<MinecraftEdition> soleEdition(String libraryId) {
		Library library = libraries.get(libraryId);
		if (library == null) return Optional.empty();

		Set<MinecraftEdition> editions = library.editions();
		return editions.size() == 1 ? Optional.of(editions.iterator().next()) : Optional.empty();
	}

	/**
	 * Lets a library check an account for an edition with the newest version of its releases of the edition. A library
	 * that cannot tell records unknown availability with its failure.
	 */
	private static StoredAccount check(StoredAccount account, Library library, MinecraftEdition edition) {
		try {
			return record(account, library, edition, derive(account, library, library.newestVersion(edition)));
		} catch (RuntimeException failure) {
			String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
			return account.withAccess(AccountAccess.unknown(edition, reason));
		}
	}

	private static DerivedSession derive(StoredAccount account, Library library, MinecraftVersion version) {
		return library.authentication().derive(AccountCredentials.builder()
				.accountId(account.getAccountId())
				.token(account.getToken())
				.cachedSession(account.cachedSession(library.library().getId()))
				.build(), version);
	}

	private static StoredAccount record(StoredAccount account, Library library, MinecraftEdition edition, DerivedSession derived) {
		String libraryId = library.library().getId();
		AuthenticationSession session = derived.getSession();
		AccountAccess access = session == null
				? AccountAccess.unavailable(edition, derived.getRefusal())
				: AccountAccess.available(edition, session.getUsername(), session.getUuid());

		return account.withToken(derived.getToken())
				.withAccess(access)
				.withSession(libraryId, session == null ? null : derived.getCachedSession());
	}

	/**
	 * One hosted library that derives sessions from stored accounts.
	 *
	 * @param library the library's description
	 * @param authentication how the library derives its session
	 * @param releases supplies the library's built-in releases, whose editions the library checks accounts for, each
	 * with the newest Minecraft version of its releases of the edition
	 */
	public record Library(
			@NotNull WorkerLibrary library,
			@NotNull WorkerAuthentication authentication,
			@NotNull Supplier<List<ProtocolRelease>> releases
	) {
		/**
		 * Returns the editions of the library's releases that sign players in online, in release order.
		 *
		 * @return editions the library checks accounts for
		 */
		@NotNull Set<MinecraftEdition> editions() {
			Set<MinecraftEdition> editions = new LinkedHashSet<>();
			for (ProtocolRelease release : online()) editions.add(release.getEdition());

			return editions;
		}

		private @NotNull MinecraftVersion newestVersion(@NotNull MinecraftEdition edition) {
			return online().stream()
					.filter(release -> release.getEdition().equals(edition))
					.flatMap(release -> release.getMinecraftVersions().stream())
					.max(Comparator.naturalOrder())
					.orElseThrow(() -> new IllegalStateException(library.getName() + " lists no release of edition '" + edition + "'"));
		}

		private @NotNull List<ProtocolRelease> online() {
			return releases.get().stream()
					.filter(release -> release.getFeatures().contains(ProtocolFeature.ONLINE_AUTHENTICATION))
					.toList();
		}
	}
}
