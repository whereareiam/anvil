package me.whereareiam.anvil.api.player.account;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Exclusively allocates authenticated accounts to simulated players.
 * <p>
 * Reservations are exclusive across every pool and player of one engine, so two scenarios cannot use
 * the same account at the same time.
 */
public interface AccountPool extends AutoCloseable {
	/**
	 * Reserves the first account that is not reserved anywhere in the engine, whatever the protocol libraries
	 * recorded about it. A player created with the lease is refused before it connects when its library recorded
	 * the account as unavailable; prefer {@link #lease(String)} or
	 * {@code PlayerManager.create(PlayerOptions, AccountPool)}, which lend only an account the player's library can
	 * use.
	 *
	 * @return account lease, held until a player claims it or it is closed
	 * @throws IllegalStateException when the pool is closed or every account is reserved
	 */
	@NotNull AccountLease lease();

	/**
	 * Reserves the first account that is not reserved anywhere in the engine and that a protocol library recorded
	 * as {@link me.whereareiam.anvil.api.type.AccountAvailability#AVAILABLE available}. The pool reads what the
	 * libraries recorded when it leases, so an account a library refused earlier in the run is skipped.
	 *
	 * <pre>{@code
	 * try (AccountPool pool = accounts.pool("testers")) {
	 *     AccountPool.AccountLease lease = pool.lease("mcprotocol");
	 * }
	 * }</pre>
	 *
	 * @param libraryId protocol library whose players will use the account
	 * @return account lease, held until a player claims it or it is closed
	 * @throws IllegalStateException when the pool is closed or holds no free account available to the library;
	 * the message names each account and why it was skipped
	 */
	@NotNull AccountLease lease(@NotNull String libraryId);

	/**
	 * Returns the accounts this pool may lease, in lease order, as the libraries recorded them when the pool was
	 * created.
	 *
	 * @return accounts eligible for this pool
	 */
	@NotNull Collection<AuthenticationAccount> accounts();

	/**
	 * Releases every lease that no player has claimed; claimed leases stay with their players.
	 */
	@Override
	void close();

	/**
	 * A single exclusive account reservation.
	 */
	interface AccountLease extends AutoCloseable {
		/**
		 * Returns the reserved account.
		 *
		 * @return reserved account metadata
		 */
		@NotNull AuthenticationAccount account();

		/**
		 * Transfers the reservation to a player. After a claim, closing the pool no longer releases
		 * the account; closing the lease, which player destruction does, still releases it.
		 *
		 * @return whether this call claimed the lease; false when it was already claimed or released
		 */
		boolean claim();

		/**
		 * Releases the reservation.
		 */
		@Override
		void close();
	}
}
