package me.whereareiam.anvil.protocol.api.model.channel;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Describes a player-scoped operation by its identifier and its explicitly registered request and response
 * schemas. Schema changes require a new operation ID. A protocol library never resolves payload class names
 * supplied by its worker or host.
 *
 * @param <Q> immutable request value; {@link Void} denotes an operation without input
 * @param <R> immutable response value; {@link Void} denotes a command without a result
 */
@Value
public class OperationDescriptor<Q, R> {
	/**
	 * Namespaced identifier shared by the handler and its callers.
	 */
	@NotNull String id;
	/**
	 * Explicit request schema.
	 */
	@NotNull Class<Q> requestType;
	/**
	 * Explicit response schema.
	 */
	@NotNull Class<R> responseType;
}
