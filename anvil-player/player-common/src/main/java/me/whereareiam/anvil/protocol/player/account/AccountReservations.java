package me.whereareiam.anvil.protocol.player.account;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountPool.AccountLease;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Holds the authenticated accounts in use across one engine, whether leased from a pool or selected
 * directly by a player, so no two players share an account. Reservations hold account IDs, so an ID
 * stored by several protocol libraries is in use at most once, whichever library signs in with it.
 */
public final class AccountReservations {
	private final Set<String> reserved = ConcurrentHashMap.newKeySet();

	/**
	 * Reserves an account selected directly by a player.
	 *
	 * @param accountId account to reserve
	 * @return whether the account was free and is now reserved
	 */
	public boolean reserve(@NotNull String accountId) {
		return reserved.add(accountId);
	}

	/**
	 * Makes a reserved account available again.
	 *
	 * @param accountId reserved account
	 */
	public void unreserve(@NotNull String accountId) {
		reserved.remove(accountId);
	}

	/**
	 * Reserves an account for a pool lease.
	 *
	 * @param account account to reserve
	 * @param onSettled called once when the lease is claimed, or closed without a claim
	 * @return unclaimed lease, or null when the account is already reserved
	 */
	@Nullable AccountLease lease(@NotNull AuthenticationAccount account, @NotNull Consumer<AccountLease> onSettled) {
		if (!reserve(account.getAccountId())) return null;

		return new Lease(account, onSettled);
	}

	private final class Lease implements AccountLease {
		private final AuthenticationAccount account;
		private final Consumer<AccountLease> onSettled;
		private boolean claimed;
		private boolean closed;

		private Lease(AuthenticationAccount account, Consumer<AccountLease> onSettled) {
			this.account = account;
			this.onSettled = onSettled;
		}

		@Override
		public @NotNull AuthenticationAccount account() {
			return account;
		}

		@Override
		public boolean claim() {
			synchronized (this) {
				if (claimed || closed) return false;
				claimed = true;
			}

			onSettled.accept(this);

			return true;
		}

		@Override
		public void close() {
			boolean settled;
			synchronized (this) {
				if (closed) return;
				closed = true;
				settled = claimed;
			}

			unreserve(account.getAccountId());
			if (!settled) onSettled.accept(this);
		}
	}
}
