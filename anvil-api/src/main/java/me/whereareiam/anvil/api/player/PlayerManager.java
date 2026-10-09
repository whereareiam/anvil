package me.whereareiam.anvil.api.player;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
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
	 * <p>An account belongs to the protocol library that stores it, so the player uses the leased
	 * account's library, whatever the scenario or engine declares.</p>
	 *
	 * @param name local simulated-player handle
	 * @param lease unclaimed account reservation
	 * @return context-owned simulated player
	 * @throws IllegalStateException when the lease was already claimed or released
	 * @throws ScenarioValidationException when the player cannot be created, for example because the leased
	 * account's library has no permitted, launchable release for the player's Minecraft version
	 */
	default @NotNull SimulatedPlayer create(@NotNull String name, @NotNull AccountPool.AccountLease lease) {
		return create(PlayerOptions.builder().name(name).authentication(AuthenticationMode.ONLINE).build(), lease);
	}

	/**
	 * Creates a player from explicit options using an exclusively reserved authenticated account. Use it when
	 * the player needs more than a name, for example {@link AuthenticationMode#ON_REQUEST} or a client version.
	 * The player claims the lease and releases it when destroyed; a creation that fails releases it at once.
	 *
	 * <p>The leased account replaces the options' account ID and protocol library, because an account belongs
	 * to the library that stores it.</p>
	 *
	 * <pre>{@code
	 * SimulatedPlayer player = players.create(PlayerOptions.builder()
	 *         .name("premium")
	 *         .authentication(AuthenticationMode.ON_REQUEST)
	 *         .build(), accounts.lease());
	 * }</pre>
	 *
	 * @param options player creation options with an authentication mode that uses an account
	 * @param lease unclaimed account reservation
	 * @return context-owned simulated player
	 * @throws IllegalArgumentException when the options' authentication mode uses no account
	 * @throws IllegalStateException when the lease was already claimed or released
	 * @throws ScenarioValidationException when the player cannot be created as declared
	 */
	default @NotNull SimulatedPlayer create(@NotNull PlayerOptions options, @NotNull AccountPool.AccountLease lease) {
		if (!options.getAuthentication().usesAccount())
			throw new IllegalArgumentException("Player '" + options.getName() + "' uses " + options.getAuthentication()
					+ " authentication, which takes no account");
		if (!lease.claim())
			throw new IllegalStateException("Account lease for '" + lease.account().getAccountId() + "' is no longer available");

		try {
			return create(options.toBuilder().protocolLibrary(lease.account().getLibraryId())
					.accountId(lease.account().getAccountId()).build());
		} catch (RuntimeException exception) {
			lease.close();
			throw exception;
		}
	}
}
