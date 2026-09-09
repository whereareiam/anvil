package me.whereareiam.anvil.protocol.api.worker;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Prepared extension installation shared by players in one native worker runtime.
 * @param <B> actual external SDK context type
 */
public interface NativeWorkerExtension<B> {
	/**
	 * Returns the capabilities this extension installs for the selected exact protocol.
	 * @return immutable capability IDs
	 */
	@NotNull Set<String> capabilities();
	/**
	 * Installs encoded operations and native listeners for one player.
	 * @param player native lifecycle and SDK context
	 * @param operations player-owned operation registration scope
	 * @return owned player binding
	 */
	@NotNull NativeBinding bind(@NotNull NativePlayer<B> player, @NotNull NativeOperations operations);
}
