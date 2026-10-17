package me.whereareiam.anvil.agent.server.api.transport;

import me.whereareiam.anvil.agent.server.api.event.PlayerEvents;
import org.jetbrains.annotations.NotNull;

/**
 * Owns a running platform-agent endpoint and its accepted connections.
 */
public interface AgentServer extends AutoCloseable {
	/**
	 * Returns where the platform agent reports players joining and leaving, which the endpoint's
	 * {@code player-events} operation answers from.
	 *
	 * @return the endpoint's player events
	 */
	@NotNull PlayerEvents playerEvents();

	/**
	 * Stops accepting requests and closes all connections owned by this endpoint.
	 */
	@Override
	void close();
}
