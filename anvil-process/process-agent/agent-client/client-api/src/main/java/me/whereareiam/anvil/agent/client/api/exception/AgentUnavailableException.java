package me.whereareiam.anvil.agent.client.api.exception;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.jetbrains.annotations.NotNull;

/**
 * A request could not begin because no open agent connection was available.
 * Communication and remote operation failures use {@link AgentException} instead.
 */
public final class AgentUnavailableException extends AgentException {
	/**
	 * Creates a failure describing the unavailable connection.
	 *
	 * @param message connection lifecycle diagnostic
	 */
	public AgentUnavailableException(@NotNull String message) {
		super(message);
	}
}
