package me.whereareiam.anvil.protocol.api.model.channel;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a player-scoped operation by its identifier and its explicitly registered request and response
 * schemas. Schema changes require a new operation ID. A protocol library never resolves payload class names
 * supplied by its worker or host.
 * <p>
 * Only a {@link Void} schema stands for no value: an operation that takes or answers anything else always carries a
 * value. An answer that may be absent is a value of its own that says so, never {@code null}.
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

	/**
	 * Checks a handler's answer against the response schema, as every worker does before it sends the answer.
	 *
	 * @param response what the operation's handler returned
	 * @return the answer as the response type, {@code null} only for a {@link Void} response
	 * @throws IllegalStateException when the handler answered {@code null} for a response other than {@link Void}
	 * @throws ClassCastException when the answer is not of the response type
	 */
	public @Nullable R response(@Nullable Object response) {
		if (response == null && responseType != Void.class)
			throw new IllegalStateException("Operation '" + id + "' answered no " + responseType.getName());

		return responseType.cast(response);
	}
}
