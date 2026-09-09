package me.whereareiam.anvil.agent.client;

import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Stable borrowed connection whose transport is replaced when its process restarts.
 */
public final class ProcessAgentClient implements AgentClient {
	private @Nullable AgentClient connection;

	/**
	 * Attaches the transport for a process generation after the previous generation has closed.
	 *
	 * @param replacement connection for the new process generation
	 * @throws IllegalStateException when the previous connection is still attached
	 */
	public synchronized void attach(@NotNull AgentClient replacement) {
		if (connection != null) {
			try (replacement) {
				throw new IllegalStateException("Close the current agent connection before attaching another");
			}
		}

		connection = replacement;
	}

	@Override
	public synchronized <T> @Nullable T request(
			@NotNull String operation,
			@Nullable Object arguments,
			@NotNull Class<T> responseType
	) {
		return connection().request(operation, arguments, responseType);
	}

	@Override
	public synchronized @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
		return connection().identity(username);
	}

	@Override
	public synchronized boolean executeCommand(@NotNull String command) {
		return connection().executeCommand(command);
	}

	@Override
	public synchronized void close() {
		AgentClient current = connection;
		connection = null;
		if (current != null) current.close();
	}

	synchronized void closeConnection(@NotNull AgentClient expected) {
		if (connection == expected) close();
	}

	private @NotNull AgentClient connection() {
		if (connection == null) throw new AgentException("Platform agent is unavailable during process restart or after cleanup");

		return connection;
	}
}
