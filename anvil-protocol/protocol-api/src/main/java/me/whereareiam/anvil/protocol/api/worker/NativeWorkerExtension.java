package me.whereareiam.anvil.protocol.api.worker;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Prepared extension installation shared by players in one native worker runtime.
 *
 * <p>An extension that cannot install one of its capabilities in this runtime, for example because
 * the loaded library release lacks a class or member the capability links against, reports it through
 * {@link #unavailable()} instead of failing the worker or the player.</p>
 *
 * @param <S> native session type of the external library
 */
public interface NativeWorkerExtension<S> {
	/**
	 * Returns the capabilities this extension currently installs for new players.
	 * @return immutable capability IDs
	 */
	@NotNull Set<String> capabilities();

	/**
	 * Returns capabilities this extension cannot install, with the reason for each. The map can grow
	 * when binding a player fails to link; such a capability is no longer bound for later players.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	default @NotNull Map<String, String> unavailable() {
		return Map.of();
	}

	/**
	 * Installs encoded operations and native listeners for one player.
	 * @param player native lifecycle and session access
	 * @param operations player-owned operation registration scope
	 * @return owned player binding
	 */
	@NotNull NativeBinding bind(@NotNull NativePlayer<S> player, @NotNull NativeOperations operations);
}
