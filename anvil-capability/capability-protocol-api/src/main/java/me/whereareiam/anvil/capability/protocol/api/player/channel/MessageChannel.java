package me.whereareiam.anvil.capability.protocol.api.player.channel;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Encoded player-message port supplied to capability codecs by an outer transport adapter.
 * The adapter preserves ordered callbacks, per-player failure isolation, and request diagnostics.
 */
public interface MessageChannel {
	/**
	 * Exchanges one encoded operation message.
	 *
	 * @param operation namespaced operation ID
	 * @param request   encoded request
	 * @return encoded response
	 */
	byte @NotNull [] request(@NotNull String operation, byte @NotNull [] request);

	/**
	 * Observes subsequent encoded player events.
	 *
	 * @param event    event ID
	 * @param listener encoded payload consumer
	 * @return owned registration
	 */
	@NotNull Subscription subscribe(@NotNull String event, @NotNull Consumer<byte[]> listener);

	/**
	 * Waits for an observation using transport-owned diagnostics.
	 *
	 * @param condition   condition to observe
	 * @param description diagnostic action description
	 * @param timeout     positive wait limit
	 */
	void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout);

	/**
	 * Returns the native capabilities available to this player.
	 *
	 * @return immutable capability IDs
	 */
	@NotNull Set<String> installedCapabilities();
}
