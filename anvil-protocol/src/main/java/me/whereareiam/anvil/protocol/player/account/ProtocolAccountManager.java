package me.whereareiam.anvil.protocol.player.account;

import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.player.account.AccountPool.AccountLease;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lists the stored accounts of one engine and creates exclusive pools over them. An account is one Microsoft
 * account that every protocol library can derive a session from; what each library recorded about it decides
 * whether a pool lends it to that library's players.
 * <p>
 * Pools and reservations work on account IDs, and an ID stays reserved until its lease closes. Named pools are
 * declared in the account directory's {@value AccountPoolFile#FILE_NAME} file.
 */
public final class ProtocolAccountManager implements AccountManager {
	private final @Nullable ProtocolAuthentication authentication;
	private final Path accountsDirectory;
	private final AccountReservations reservations;

	/**
	 * Creates an account manager over the installed libraries' account workflow.
	 *
	 * @param authentication the account workflow of the installed libraries, or null when none plays online
	 * @param accountsDirectory directory holding account files and pool declarations
	 * @param reservations engine-wide reservations shared with directly selected accounts
	 */
	public ProtocolAccountManager(
			@Nullable ProtocolAuthentication authentication,
			@NotNull Path accountsDirectory,
			@NotNull AccountReservations reservations
	) {
		this.authentication = authentication;
		this.accountsDirectory = accountsDirectory;
		this.reservations = reservations;
	}

	/**
	 * Lists the stored accounts with what each library recorded about them.
	 *
	 * @return account metadata without credential material, ordered by account ID
	 */
	@Override
	public @NotNull Collection<AuthenticationAccount> list() {
		if (authentication == null) return List.of();

		return List.copyOf(authentication.accounts());
	}

	/**
	 * Creates a pool holding the account each supplied ID names.
	 *
	 * @param accountIds distinct account IDs in lease order
	 * @return account pool owned by the caller
	 * @throws IllegalArgumentException when no ID is supplied, an ID repeats, or no account is stored under an ID
	 */
	@Override
	public @NotNull AccountPool pool(@NotNull Collection<String> accountIds) {
		if (accountIds.isEmpty()) throw new IllegalArgumentException("Account pool must contain at least one account");

		Map<String, AuthenticationAccount> stored = stored();
		List<AuthenticationAccount> selected = new ArrayList<>();
		Set<String> unique = new LinkedHashSet<>();
		for (String accountId : accountIds) {
			if (!unique.add(accountId)) throw new IllegalArgumentException("Duplicate account in pool: " + accountId);

			AuthenticationAccount account = stored.get(accountId);
			if (account == null) throw new IllegalArgumentException("Unknown account: " + accountId);
			selected.add(account);
		}

		return new Pool(selected);
	}

	@Override
	public @NotNull AccountPool pool(@NotNull String poolName) {
		if (poolName.isBlank()) throw new IllegalArgumentException("Account pool name must not be blank");

		return pool(new AccountPoolFile(accountsDirectory).accountIds(poolName));
	}

	private Map<String, AuthenticationAccount> stored() {
		return list().stream().collect(Collectors.toMap(
				AuthenticationAccount::getAccountId,
				Function.identity(),
				(first, second) -> first,
				LinkedHashMap::new
		));
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

		/**
		 * Leases the first free account the library recorded as available, reading what the libraries recorded now,
		 * so an account a library refused earlier in the run is skipped.
		 */
		@Override
		public synchronized @NotNull AccountLease lease(@NotNull String libraryId) {
			if (closed) throw new IllegalStateException("Account pool is closed");

			Map<String, AuthenticationAccount> stored = stored();
			List<String> skipped = new ArrayList<>();
			for (AuthenticationAccount pooled : accounts) {
				AuthenticationAccount account = stored.get(pooled.getAccountId());
				if (account == null) {
					skipped.add(pooled.getAccountId() + " (no longer stored)");
					continue;
				}

				AccountAccess access = account.access(libraryId);
				if (!access.isAvailable()) {
					skipped.add(account.getAccountId() + " (" + access.describe() + ")");
					continue;
				}

				AccountLease lease = reservations.lease(account, this::forget);
				if (lease == null) {
					skipped.add(account.getAccountId() + " (in use)");
					continue;
				}

				unclaimed.add(lease);
				return lease;
			}

			throw new IllegalStateException("No account of the pool is available to protocol library '" + libraryId + "': "
					+ String.join(", ", skipped));
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
