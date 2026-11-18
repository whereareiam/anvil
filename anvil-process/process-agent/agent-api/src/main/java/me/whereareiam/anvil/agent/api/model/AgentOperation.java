package me.whereareiam.anvil.agent.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import org.jetbrains.annotations.NotNull;

/**
 * Shared request and response contract for a platform-agent operation.
 *
 * <p>Keep this descriptor and its models in the extension's API artifact so the host and
 * platform handler agree on the wire contract. Models must be supported by the selected transport.</p>
 *
 * @param <Q> request model
 * @param <R> response model
 */
@Value
@Builder
public class AgentOperation<Q, R> {
	/**
	 * Globally unique wire name. Extension operations begin with the contributing provider's ID
	 * and a dot; built-in operations use the reserved names in
	 * {@link AgentOperations}.
	 */
	@NotNull String name;
	/**
	 * Model decoded before the platform handler is invoked. {@link Void} declares an operation
	 * without a request value; its caller and handler use {@code null}.
	 */
	@NotNull Class<Q> requestType;
	/**
	 * Model decoded by the host connection after execution.
	 */
	@NotNull Class<R> responseType;
}
