package me.whereareiam.anvil.protocol.adapter.api.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Host-side request and event channel exposed to packet-backed player capabilities.
 */
public interface ProtocolPlayerConnection {
	/**
	 * Executes a namespaced worker operation and ignores its result.
	 *
	 * @param operation operation name owned by a capability
	 * @param arguments request argument writer
	 */
	default void execute(
			@NotNull String operation,
			@NotNull Consumer<ObjectNode> arguments
	) {
		request(operation, arguments);
	}

	/**
	 * Executes a namespaced worker operation.
	 *
	 * @param operation operation name owned by a capability
	 * @param arguments request argument writer
	 * @return worker response
	 */
	@NotNull JsonNode request(
			@NotNull String operation,
			@NotNull Consumer<ObjectNode> arguments
	);

	/**
	 * Subscribes to a namespaced event emitted for this player.
	 *
	 * @param event event name
	 * @param listener player-scoped listener
	 */
	void subscribe(@NotNull String event, @NotNull Consumer<JsonNode> listener);

	/**
	 * Waits until a host-side observation becomes true.
	 *
	 * @param condition observation predicate
	 * @param description diagnostic action description
	 * @param timeout maximum wait
	 */
	void await(
			@NotNull BooleanSupplier condition,
			@NotNull String description,
			@NotNull Duration timeout
	);

	/**
	 * Returns capabilities installed in the isolated worker process.
	 *
	 * @return immutable worker capability IDs
	 */
	@NotNull Set<String> workerCapabilities();

}
