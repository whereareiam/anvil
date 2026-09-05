package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Stable borrowed connection whose transport is replaced when its process restarts.
 */
final class ScenarioAgent implements AgentClient {
	private @Nullable AgentClient connection;

	ScenarioAgent(@NotNull AgentClient connection) {
		this.connection = connection;
	}

	synchronized void replace(@NotNull AgentClient replacement) {
		if (connection != null) {
			replacement.close();
			throw new IllegalStateException("Detach the existing agent before replacing its connection");
		}
		connection = replacement;
	}

	@Override
	public synchronized <T> T request(@NotNull String operation, @NotNull Object arguments, @NotNull Class<T> responseType) {
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

	private @NotNull AgentClient connection() {
		if (connection == null) throw new AgentException("Platform agent is unavailable during process restart or after cleanup");
		return connection;
	}
}
