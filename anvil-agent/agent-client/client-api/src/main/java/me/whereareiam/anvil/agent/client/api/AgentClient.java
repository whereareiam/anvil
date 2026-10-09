package me.whereareiam.anvil.agent.client.api;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Typed host-side contract for a running platform agent.
 */
public interface AgentClient extends AgentConnection {
	/**
	 * Observes a player through this process's native platform API.
	 *
	 * @param username player name
	 * @return identity observed by the platform, or empty when the platform does not observe the player
	 * @throws AgentUnavailableException when no open connection is available before the request
	 * @throws AgentException when communication or the remote operation fails
	 */
	@NotNull Optional<AgentIdentity> identity(@NotNull String username);

	/**
	 * Executes a command through this process's platform console.
	 *
	 * @param command command without a leading slash
	 * @return whether the platform accepted the command
	 * @throws AgentUnavailableException when no open connection is available before the request
	 * @throws AgentException when communication or the remote operation fails
	 */
	boolean executeCommand(@NotNull String command);

	/**
	 * Closes the platform-agent connection.
	 */
	@Override
	void close();
}
