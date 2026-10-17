package me.whereareiam.anvil.protocol.api.channel;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Player-scoped named-message transport. Payload bytes are opaque to the protocol library;
 * outer codec adapters own their typed schemas and serialization.
 * Callbacks execute in player order outside the shared response reader.
 */
public interface ProtocolChannel {
	/**
	 * Exchanges one encoded operation payload and returns its encoded response.
	 * @param operation namespaced operation ID
	 * @param request encoded request bytes
	 * @return encoded response bytes
	 */
	@NotNull byte[] request(@NotNull String operation, byte @NotNull [] request);

	/**
	 * Registers a listener for subsequent messages. A listener failure is isolated to this player
	 * and reported on its subsequent requests, waits, and cleanup.
	 * @param event namespaced event ID
	 * @param listener encoded event observer
	 * @return owned registration
	 */
	@NotNull ProtocolSubscription subscribe(@NotNull String event, @NotNull Consumer<byte[]> listener);

	/**
	 * Waits for a host observation with this player's transport diagnostics.
	 * @param condition observation predicate
	 * @param description action description
	 * @param timeout maximum positive wait
	 */
	void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout);

	/**
	 * Returns capability IDs installed in this player's native worker.
	 * @return immutable installed IDs
	 */
	@NotNull Set<String> installedCapabilities();

	/**
	 * Returns capabilities that the native worker could not install for this player, with the reason
	 * for each, such as the exact class or member missing from the loaded library release.
	 * Capability composition skips the providers of every capability that is not installed instead of
	 * failing the player, and reports these reasons for them.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	default @NotNull Map<String, String> unavailableCapabilities() {
		return Map.of();
	}
}
