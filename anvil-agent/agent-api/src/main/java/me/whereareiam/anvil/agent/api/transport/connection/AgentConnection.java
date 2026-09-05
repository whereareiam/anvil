package me.whereareiam.anvil.agent.api.transport.connection;

import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Low-level host-side connection to one authenticated platform agent.
 *
 * <p>Callers supply request and response models; the connection owns their serialization.
 * Engine and platform integrations use the typed {@link AgentClient} facade.</p>
 */
public interface AgentConnection extends AutoCloseable {
	/**
	 * Invokes a shared typed operation contract.
	 *
	 * @param operation request and response descriptor
	 * @param request request model
	 * @param <Q> request type
	 * @param <R> response type
	 * @return decoded result, or {@code null} when the remote operation has no result
	 */
	default <Q, R> @Nullable R request(@NotNull AgentOperation<Q, R> operation, @NotNull Q request) {
		return request(operation.getName(), request, operation.getResponseType());
	}

	/**
	 * Sends one operation and decodes its response as the requested model.
	 *
	 * @param operation stable operation name
	 * @param arguments request model supported by the selected transport
	 * @param responseType response model supported by the selected transport
	 * @param <T> response type
	 * @return decoded result, or {@code null} when the remote operation has no result
	 */
	<T> @Nullable T request(
			@NotNull String operation,
			@NotNull Object arguments,
			@NotNull Class<T> responseType
	);

	/**
	 * Closes the connection.
	 */
	@Override
	void close();
}
