package me.whereareiam.anvil.agent.api.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Shared request and response contract for a namespaced platform-agent operation.
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
	 * Globally unique name beginning with the contributing provider's ID and a dot.
	 */
	@NotNull String name;
	/**
	 * Model decoded before the platform handler is invoked.
	 */
	@NotNull Class<Q> requestType;
	/**
	 * Model decoded by the host connection after execution.
	 */
	@NotNull Class<R> responseType;
}
