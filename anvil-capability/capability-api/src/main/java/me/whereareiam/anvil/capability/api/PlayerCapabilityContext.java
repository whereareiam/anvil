package me.whereareiam.anvil.capability.api;

import me.whereareiam.anvil.api.player.PlayerCapability;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Player-scoped services exposed while a provider creates its capability.
 */
public interface PlayerCapabilityContext {
	/**
	 * Returns the configured player name.
	 *
	 * @return player name
	 */
	@NotNull String playerName();

	/**
	 * Returns the resolved native client version.
	 *
	 * @return Minecraft client version
	 */
	@NotNull String clientVersion();

	/**
	 * Finds an execution service exposed by the selected protocol backend or scenario runtime.
	 *
	 * @param type service type
	 * @param <T> service type
	 * @return matching service when available
	 */
	@NotNull <T> Optional<T> findService(@NotNull Class<T> type);

	/**
	 * Resolves a required execution service.
	 *
	 * @param type service type
	 * @param <T> service type
	 * @return matching service
	 * @throws CapabilityException when the selected backend or runtime does not expose the service
	 */
	default @NotNull <T> T requireService(@NotNull Class<T> type) {
		return findService(type).orElseThrow(() -> new CapabilityException(
				"Player '" + playerName() + "' does not expose required service " + type.getName()
		));
	}

	/**
	 * Finds a capability created by a declared provider dependency.
	 *
	 * @param type capability type
	 * @param <C> capability type
	 * @return matching capability when already created
	 */
	@NotNull <C extends PlayerCapability> Optional<C> findCapability(@NotNull Class<C> type);

	/**
	 * Registers cleanup that runs when the owning simulated player is destroyed.
	 *
	 * @param action player-scoped cleanup action
	 */
	void onDestroy(@NotNull Runnable action);

	/**
	 * Resolves a capability created by a declared provider dependency.
	 *
	 * @param type capability type
	 * @param <C> capability type
	 * @return matching capability
	 * @throws CapabilityException when the required capability is unavailable
	 */
	default @NotNull <C extends PlayerCapability> C requireCapability(@NotNull Class<C> type) {
		return findCapability(type).orElseThrow(() -> new CapabilityException(
				"Player '" + playerName() + "' does not expose required capability " + type.getName()
		));
	}
}
