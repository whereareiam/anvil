package me.whereareiam.anvil.agent.client.api.connection;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
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
	 * Reports whether a connection is currently attached and locally open for requests.
	 * This is a local lifecycle snapshot, not a remote health check. A subsequent request
	 * can still fail if the connection closes or communication fails.
	 *
	 * @return whether requests can currently be attempted
	 */
	boolean available();

	/**
	 * Invokes a shared typed operation contract.
	 *
	 * @param operation request and response descriptor
	 * @param request request model, or {@code null} when the operation declares a {@link Void} request
	 * @param <Q> request type
	 * @param <R> response type
	 * @return decoded result, or {@code null} when the remote operation has no result
	 * @throws AgentUnavailableException when no open connection is available before the request
	 * @throws AgentException when communication or the remote operation fails
	 */
	default <Q, R> @Nullable R request(@NotNull AgentOperation<Q, R> operation, @Nullable Q request) {
		return request(operation.getName(), request, operation.getResponseType());
	}

	/**
	 * Sends one operation and decodes its response as the requested model.
	 *
	 * @param operation stable operation name
	 * @param arguments request model supported by the selected transport, or {@code null} for a
	 *                  registered operation with a {@link Void} request
	 * @param responseType response model supported by the selected transport
	 * @param <T> response type
	 * @return decoded result, or {@code null} when the remote operation has no result
	 * @throws AgentUnavailableException when no open connection is available before the request
	 * @throws AgentException when communication or the remote operation fails
	 */
	<T> @Nullable T request(
			@NotNull String operation,
			@Nullable Object arguments,
			@NotNull Class<T> responseType
	);

	/**
	 * Closes the connection.
	 */
	@Override
	void close();
}
