package me.whereareiam.anvil.protocol.api.channel;

import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Executes typed operations and observes typed events of one player's native worker. The protocol library owns
 * serialization and transport and decodes only the schemas a descriptor declares.
 */
public interface ProtocolChannel {
	/**
	 * Executes one operation registered by a worker extension of this player.
	 *
	 * @param operation operation identity and request/response schemas
	 * @param request request value, or {@code null} for a {@link Void} request
	 * @param <Q> request type
	 * @param <R> response type
	 * @return response value, or {@code null} for a {@link Void} response
	 * @throws IllegalArgumentException when the request is {@code null} for an operation that requires one, or a
	 * value does not match the operation's schemas
	 * @throws IllegalStateException when the player has been destroyed, one of its event callbacks failed, or the
	 * operation failed in the worker
	 */
	<Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request);

	/**
	 * Observes subsequent events until the subscription or player is closed. Events for one player are delivered in
	 * order outside its response reader, so a callback may execute channel requests. Closing the subscription also
	 * cancels its queued deliveries. A callback or decoding failure is reported on the affected player's subsequent
	 * operations, waits and cleanup; it does not fail other players' channels.
	 *
	 * @param event event identifier and payload schema
	 * @param listener player-scoped observer; a {@link Void} event delivers {@code null}
	 * @param <E> event payload type
	 * @return owned listener registration
	 */
	<E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> event, @NotNull Consumer<E> listener);

	/**
	 * Waits for an observation, retaining the library's diagnostics on timeout.
	 *
	 * @param condition observation predicate
	 * @param description diagnostic action description
	 * @param timeout maximum positive wait
	 */
	void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout);

	/**
	 * Returns the capability IDs whose worker extensions are installed for this player. Capability composition skips
	 * every protocol-backed provider whose capability ID is missing here, together with the providers that depend on
	 * its capability.
	 *
	 * @return immutable capability IDs
	 */
	@NotNull Set<String> installedCapabilities();

	/**
	 * Returns capabilities that the native worker could not install for this player, with the reason for each, such
	 * as the exact class or member missing from the loaded library release. A skipped capability reports this reason
	 * when it is requested; a capability missing from both this map and {@link #installedCapabilities()} reports
	 * that the library's worker does not install it.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	default @NotNull Map<String, String> unavailableCapabilities() {
		return Map.of();
	}
}
