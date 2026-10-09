package me.whereareiam.anvil.protocol.player.account;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.player.account.AccountPool.AccountLease;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates the stored accounts of every installed protocol library and creates exclusive pools
 * for one engine. Account IDs are library-local: each account keeps the identifier of the library
 * that stores it, and a player signs in with the account its selected library stores under the ID.
 * <p>
 * Pools and reservations work on account IDs. A pool holds one account per ID, so it refuses an ID that
 * several libraries store: a lease then always describes the account its player signs in with, and the
 * player selects the leased account's library. The ID stays reserved until the lease closes.
 * Named pools are declared in the account directory's {@value AccountPoolFile#FILE_NAME} file.
 */
public final class ProtocolAccountManager implements AccountManager {
	private final ProtocolLibraryRegistry libraries;
	private final Path accountsDirectory;
	private final AccountReservations reservations;

	/**
	 * Creates an account manager backed by every installed protocol library offering authentication.
	 *
	 * @param libraries installed protocol libraries that own stored credentials
	 * @param accountsDirectory directory holding account files and pool declarations
	 * @param reservations engine-wide reservations shared with directly selected accounts
	 */
	public ProtocolAccountManager(
			@NotNull ProtocolLibraryRegistry libraries,
			@NotNull Path accountsDirectory,
			@NotNull AccountReservations reservations
	) {
		this.libraries = libraries;
		this.accountsDirectory = accountsDirectory;
		this.reservations = reservations;
	}

	/**
	 * Lists the accounts of every installed library in discovery order. An account ID stored by several
	 * libraries appears once per library.
	 *
	 * @return account metadata without credential material
	 */
	@Override
	public @NotNull Collection<AuthenticationAccount> list() {
		List<AuthenticationAccount> accounts = new ArrayList<>();
		for (ProtocolLibraryProvider library : libraries.all())
			accounts.addAll(library.authentication(accountsDirectory)
					.map(ProtocolAuthentication::accounts)
					.orElse(List.of()));

		return List.copyOf(accounts);
	}

	/**
	 * Creates a pool holding the one account each supplied ID names.
	 *
	 * @param accountIds distinct account IDs in lease order
	 * @return account pool owned by the caller
	 * @throws IllegalArgumentException when no ID is supplied, an ID repeats, no library stores an ID, or
	 * several libraries store an ID, which would leave the pool unable to tell which account a lease holds
	 */
	@Override
	public @NotNull AccountPool pool(@NotNull Collection<String> accountIds) {
		if (accountIds.isEmpty()) throw new IllegalArgumentException("Account pool must contain at least one account");
		Map<String, List<AuthenticationAccount>> available = new LinkedHashMap<>();
		for (AuthenticationAccount account : list())
			available.computeIfAbsent(account.getAccountId(), id -> new ArrayList<>()).add(account);
		List<AuthenticationAccount> selected = new ArrayList<>();
		Set<String> unique = new LinkedHashSet<>();
		for (String accountId : accountIds) {
			if (!unique.add(accountId)) throw new IllegalArgumentException("Duplicate account in pool: " + accountId);
			List<AuthenticationAccount> stored = available.get(accountId);
			if (stored == null) throw new IllegalArgumentException("Unknown account: " + accountId);
			if (stored.size() > 1)
				throw new IllegalArgumentException("Account '" + accountId + "' is stored by several protocol libraries "
						+ stored.stream().map(AuthenticationAccount::getLibraryId).toList()
						+ "; a pool leases one account per ID, so store each pooled ID in one library only");

			selected.add(stored.getFirst());
		}
		return new Pool(selected);
	}

	@Override
	public @NotNull AccountPool pool(@NotNull String poolName) {
		if (poolName.isBlank()) throw new IllegalArgumentException("Account pool name must not be blank");

		return pool(new AccountPoolFile(accountsDirectory).accountIds(poolName));
	}

	private final class Pool implements AccountPool {
		private final List<AuthenticationAccount> accounts;
		private final Set<AccountLease> unclaimed = new LinkedHashSet<>();
		private boolean closed;

		private Pool(List<AuthenticationAccount> accounts) {
			this.accounts = List.copyOf(accounts);
		}

		@Override
		public synchronized @NotNull AccountLease lease() {
			if (closed) throw new IllegalStateException("Account pool is closed");

			for (AuthenticationAccount account : accounts) {
				AccountLease lease = reservations.lease(account, this::forget);
				if (lease == null) continue;

				unclaimed.add(lease);
				return lease;
			}

			throw new IllegalStateException("Account pool is exhausted");
		}

		@Override
		public @NotNull Collection<AuthenticationAccount> accounts() {
			return accounts;
		}

		@Override
		public void close() {
			List<AccountLease> closing;
			synchronized (this) {
				closed = true;
				closing = List.copyOf(unclaimed);
			}

			// Closed outside the pool lock: a lease notifies the pool when it finishes.
			closing.forEach(AccountLease::close);
		}

		private synchronized void forget(AccountLease lease) {
			unclaimed.remove(lease);
		}
	}
}
