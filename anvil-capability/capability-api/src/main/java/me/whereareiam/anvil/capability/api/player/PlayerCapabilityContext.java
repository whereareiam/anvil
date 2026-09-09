package me.whereareiam.anvil.capability.api.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;

/**
 * Player identity, observations, declared dependencies, and cleanup available during capability creation.
 * The context borrows observations from the scenario and exposes no connection, request channel,
 * or external SDK service. Dependencies always belong to this player.
 */
public interface PlayerCapabilityContext extends CapabilityContext<PlayerCapability> {
	/**
	 * Returns the configured player name.
	 *
	 * @return player name
	 */
	@NotNull String playerName();

	/**
	 * Returns the selected Minecraft client version.
	 *
	 * @return client version
	 */
	@NotNull String clientVersion();

	/**
	 * Returns identity and route observations for this player.
	 *
	 * @return borrowed observation source
	 */
	@NotNull PlayerObservation observation();

	/**
	 * Registers cleanup for permanent destruction of the owning player.
	 * Actions run in reverse registration order, including rollback after failed capability creation.
	 *
	 * @param action player-scoped cleanup action
	 */
	void onDestroy(@NotNull Runnable action);

	/**
	 * Registers cleanup for permanent destruction of the owning player.
	 *
	 * @param action player-scoped cleanup action
	 */
	@Override
	default void onClose(@NotNull Runnable action) {
		onDestroy(action);
	}

	/**
	 * Resolves a capability created by an explicitly declared provider dependency.
	 *
	 * @param type public player capability interface
	 * @param <C> requested capability type
	 * @return declared capability belonging to this player
	 * @throws CapabilityException when the dependency is unavailable or undeclared
	 */
	@Override
	default @NotNull <C extends PlayerCapability> C requireCapability(@NotNull Class<C> type) {
		return findCapability(type).orElseThrow(() -> new CapabilityException(
				"Player '" + playerName() + "' does not expose required capability " + type.getName()
		));
	}
}
