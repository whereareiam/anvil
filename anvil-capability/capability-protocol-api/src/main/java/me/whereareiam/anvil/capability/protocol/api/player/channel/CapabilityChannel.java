package me.whereareiam.anvil.capability.protocol.api.player.channel;

import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Executes typed capability operations and observes events for one backend-owned player.
 * Serialization and transport are owned by the backend, not capability implementations.
 */
public interface CapabilityChannel extends RequestChannel {
	/**
	 * Observes subsequent events until the subscription or player is closed. Events for one
	 * player are delivered in order outside its response reader, so a callback may execute
	 * channel requests. Closing the subscription also cancels its queued deliveries.
	 * A callback or decoding failure is reported on the affected player's subsequent
	 * operations, waits, and cleanup; it must not fail other players' request channels.
	 *
	 * @param eventDescriptor event identifier and payload schema
	 * @param listener player-scoped observer
	 * @param <E> event payload type
	 * @return owned listener registration
	 */
	@NotNull <E> Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener);

	/**
	 * Waits for an observation, retaining backend diagnostics on timeout.
	 *
	 * @param condition observation predicate
	 * @param description diagnostic action description
	 * @param timeout maximum positive wait
	 */
	void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout);

	/**
	 * Returns the capability IDs installed in the selected native binding.
	 *
	 * @return immutable capability IDs
	 */
	@NotNull Set<String> installedCapabilities();
}
