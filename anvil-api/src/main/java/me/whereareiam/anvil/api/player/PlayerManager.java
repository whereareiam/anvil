package me.whereareiam.anvil.api.player;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Context-owned factory and registry for dynamically created simulated players.
 */
public interface PlayerManager extends AutoCloseable {
	/**
	 * Creates a disconnected offline player using the scenario entrypoint and newest compatible
	 * verified client version.
	 *
	 * @param name unique player name
	 * @return context-owned simulated player
	 * @throws ScenarioValidationException when the player cannot be created as declared, for example because
	 * no installed protocol library has a permitted, launchable release for its Minecraft version
	 */
	@NotNull SimulatedPlayer create(@NotNull String name);

	/**
	 * Creates a disconnected player using explicit overrides where supplied.
	 *
	 * @param options player creation options
	 * @return context-owned simulated player
	 * @throws ScenarioValidationException when the player cannot be created as declared, for example because
	 * its protocol library has no permitted, launchable release for its Minecraft version or its account
	 * cannot sign in
	 */
	@NotNull SimulatedPlayer create(@NotNull PlayerOptions options);

	/**
	 * Returns an immutable snapshot of currently registered players.
	 *
	 * @return registered players
	 */
	@NotNull Collection<SimulatedPlayer> all();

	/**
	 * Resolves a registered player by name.
	 *
	 * @param name player name
	 * @return matching player
	 */
	@NotNull SimulatedPlayer get(@NotNull String name);

	/**
	 * Permanently destroys and unregisters every player.
	 */
	void destroyAll();

	/**
	 * Releases all players and player-manager resources.
	 *
	 * <p>The default implementation preserves the lightweight global API contract. Runtime
	 * implementations may additionally release protocol, agent, or capability resources.</p>
	 */
	@Override
	default void close() {
		destroyAll();
	}

	/**
	 * Creates a player using an exclusively reserved authenticated account. The player claims the lease
	 * and releases it when destroyed; a creation that fails releases it at once.
	 *
	 * <p>The player selects its protocol library as any other player does. When the leased account was recorded as
	 * unavailable for the edition of the servers the player reaches, the player is refused before it connects.</p>
	 *
	 * @param name local simulated-player handle
	 * @param lease unclaimed account reservation
	 * @return context-owned simulated player
	 * @throws IllegalStateException when the lease was already claimed or released
	 * @throws ScenarioValidationException when the player cannot be created, for example because its library has
	 * no permitted, launchable release for the player's Minecraft version or the account was recorded as unavailable
	 * for the player's edition
	 */
	default @NotNull SimulatedPlayer create(@NotNull String name, @NotNull AccountPool.AccountLease lease) {
		return create(PlayerOptions.builder().name(name).login(PlayerLogin.leased(AuthenticationMode.ONLINE)).build(), lease);
	}

	/**
	 * Creates a player from explicit options using an exclusively reserved authenticated account. Use it when
	 * the player needs more than a name, for example {@link AuthenticationMode#ON_REQUEST}, a connection or a
	 * client version. The options declare their authentication mode through {@link PlayerLogin#leased}.
	 * The player claims the lease and releases it when destroyed; a creation that fails releases it at once.
	 *
	 * <p>The leased account replaces the options' login. The player selects its protocol library as any other
	 * player does; when the account was recorded as unavailable for the player's edition, the player is refused
	 * before it connects. {@link #create(PlayerOptions, AccountPool)} leases a suitable account instead.</p>
	 *
	 * <pre>{@code
	 * SimulatedPlayer player = players.create(PlayerOptions.builder()
	 *         .name("premium")
	 *         .login(PlayerLogin.leased(AuthenticationMode.ON_REQUEST))
	 *         .build(), accounts.lease());
	 * }</pre>
	 *
	 * @param options player creation options with a {@link PlayerLogin#isLeased() leased} login
	 * @param lease unclaimed account reservation
	 * @return context-owned simulated player
	 * @throws IllegalArgumentException when the options' login is not a leased one
	 * @throws IllegalStateException when the lease was already claimed or released
	 * @throws ScenarioValidationException when the player cannot be created as declared
	 */
	default @NotNull SimulatedPlayer create(@NotNull PlayerOptions options, @NotNull AccountPool.AccountLease lease) {
		PlayerLogin login = leasedLogin(options);
		if (!lease.claim())
			throw new IllegalStateException("Account lease for '" + lease.account().getAccountId() + "' is no longer available");

		try {
			return create(options.toBuilder()
					.login(PlayerLogin.account(login.getAuthentication(), lease.account().getAccountId()))
					.build());
		} catch (RuntimeException exception) {
			lease.close();
			throw exception;
		}
	}

	/**
	 * Creates a player from explicit options with an account it leases from a pool for the edition of the servers
	 * it reaches, as {@link AccountPool#lease(me.whereareiam.anvil.api.model.Edition)} leases it, so it never
	 * connects with an account its edition cannot use. The player releases the lease when destroyed; a creation that
	 * fails releases it at once.
	 *
	 * <pre>{@code
	 * SimulatedPlayer player = players.create(PlayerOptions.builder()
	 *         .name("premium")
	 *         .login(PlayerLogin.leased(AuthenticationMode.ONLINE))
	 *         .build(), pool);
	 * }</pre>
	 *
	 * @param options player creation options with a {@link PlayerLogin#isLeased() leased} login
	 * @param pool pool to lease the account from
	 * @return context-owned simulated player
	 * @throws IllegalArgumentException when the options' login is not a leased one
	 * @throws IllegalStateException when the pool is closed or holds no free account available to the player's
	 * edition; the message names each account and why it was skipped
	 * @throws ScenarioValidationException when the player cannot be created as declared
	 * @throws UnsupportedOperationException when this manager cannot tell a player's edition before creating it
	 */
	default @NotNull SimulatedPlayer create(@NotNull PlayerOptions options, @NotNull AccountPool pool) {
		throw new UnsupportedOperationException("Leasing an account for the player's edition is unavailable");
	}

	private static @NotNull PlayerLogin leasedLogin(@NotNull PlayerOptions options) {
		PlayerLogin login = options.getLogin();
		if (!login.isLeased())
			throw new IllegalArgumentException("Player '" + options.getName() + "' declares a " + login.getAuthentication()
					+ " login that takes no lease; declare PlayerLogin.leased(AuthenticationMode) to sign in with a leased account");
		return login;
	}
}
