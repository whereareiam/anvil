package me.whereareiam.anvil.api.player.account;

import me.whereareiam.anvil.api.model.Edition;
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
	 * Reserves the first account that is not reserved anywhere in the engine, whatever was recorded about it. A
	 * player created with the lease is refused before it connects when the account was recorded as unavailable for
	 * the edition of the servers the player reaches; prefer {@link #lease(Edition)} or
	 * {@code PlayerManager.create(PlayerOptions, AccountPool)}, which lend only an account the player's edition can
	 * use.
	 *
	 * @return account lease, held until a player claims it or it is closed
	 * @throws IllegalStateException when the pool is closed or every account is reserved
	 */
	@NotNull AccountLease lease();

	/**
	 * Reserves the first account that is not reserved anywhere in the engine and that was recorded as
	 * {@link me.whereareiam.anvil.api.type.AccountAvailability#AVAILABLE available} for an edition. The pool reads the
	 * records when it leases, so an account refused earlier in the run is skipped.
	 *
	 * <p>When no free account is recorded as available, the pool checks each free account whose availability for the
	 * edition is {@link me.whereareiam.anvil.api.type.AccountAvailability#UNKNOWN unknown} in lease order: an installed
	 * protocol library of the edition derives a session from it once, over the network, the result is recorded, and
	 * the first account that turns out available is lent.</p>
	 *
	 * <pre>{@code
	 * try (AccountPool pool = accounts.pool("testers")) {
	 *     AccountPool.AccountLease lease = pool.lease(Edition.of("example"));
	 * }
	 * }</pre>
	 *
	 * @param edition edition of the servers the players that use the account reach
	 * @return account lease, held until a player claims it or it is closed
	 * @throws IllegalStateException when the pool is closed or holds no free account available for the edition;
	 * the message names each account and why it was skipped
	 */
	@NotNull AccountLease lease(@NotNull Edition edition);

	/**
	 * Returns the accounts this pool may lease, in lease order, as they were recorded when the pool was created.
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
