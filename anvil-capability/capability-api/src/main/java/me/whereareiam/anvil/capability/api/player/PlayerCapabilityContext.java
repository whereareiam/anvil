package me.whereareiam.anvil.capability.api.player;

import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Player identity, declared dependencies, and cleanup available during capability creation. The context
 * exposes no connection, request channel, or external SDK service. Dependencies always belong to this player.
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
	 * Returns the profile the player's client asks for when it logs in.
	 *
	 * @return requested profile
	 */
	@NotNull PlayerProfile requestedProfile();

	/**
	 * Returns the profile the process the player joined granted at the latest login, read when called.
	 *
	 * @return granted profile, or null before the player logged in and after it disconnected
	 */
	@Nullable PlayerProfile grantedProfile();

	/**
	 * Returns how the player connects, as its options declare it: the scenario server, proxy or remote it joins, where
	 * a capability that follows the player's connection through the scenario's processes starts, the host it
	 * announces and the address it connects from. The target is always named, also when the player joins the
	 * entrypoint. A remote is none of the scenario's processes.
	 *
	 * @return the player's connection, with its target process
	 */
	@NotNull PlayerConnection connection();

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
