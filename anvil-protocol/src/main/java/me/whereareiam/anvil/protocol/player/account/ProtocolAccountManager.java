package me.whereareiam.anvil.protocol.player.account;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.player.account.AccountPool.AccountLease;
import me.whereareiam.anvil.protocol.api.provider.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
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
 * Discovers provider-backed accounts and creates exclusive pools for one engine.
 * <p>
 * Named pools are declared in the account directory's {@value AccountPoolFile#FILE_NAME} file.
 */
public final class ProtocolAccountManager implements AccountManager {
	private final ProtocolProvider provider;
	private final Path accountsDirectory;
	private final AccountReservations reservations;

	/**
	 * Creates an account manager backed by the selected protocol provider.
	 *
	 * @param provider protocol provider that owns stored credentials
	 * @param accountsDirectory directory holding account files and pool declarations
	 * @param reservations engine-wide reservations shared with directly selected accounts
	 */
	public ProtocolAccountManager(
			@NotNull ProtocolProvider provider,
			@NotNull Path accountsDirectory,
			@NotNull AccountReservations reservations
	) {
		this.provider = provider;
		this.accountsDirectory = accountsDirectory;
		this.reservations = reservations;
	}

	@Override
	public @NotNull Collection<AuthenticationAccount> list() {
		return provider.authentication(accountsDirectory)
				.map(ProtocolAuthentication::accounts)
				.orElse(List.of());
	}

	@Override
	public @NotNull AccountPool pool(@NotNull Collection<String> accountIds) {
		if (accountIds.isEmpty()) throw new IllegalArgumentException("Account pool must contain at least one account");
		Map<String, AuthenticationAccount> available = new LinkedHashMap<>();
		for (AuthenticationAccount account : list()) available.put(account.getAccountId(), account);
		List<AuthenticationAccount> selected = new ArrayList<>();
		Set<String> unique = new LinkedHashSet<>();
		for (String accountId : accountIds) {
			if (!unique.add(accountId)) throw new IllegalArgumentException("Duplicate account in pool: " + accountId);
			AuthenticationAccount account = available.get(accountId);
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
			List<AccountLease> releasing;
			synchronized (this) {
				closed = true;
				releasing = List.copyOf(unclaimed);
			}

			// Closed outside the pool lock: a lease notifies the pool when it finishes.
			releasing.forEach(AccountLease::close);
		}

		private synchronized void forget(AccountLease lease) {
			unclaimed.remove(lease);
		}
	}
}
