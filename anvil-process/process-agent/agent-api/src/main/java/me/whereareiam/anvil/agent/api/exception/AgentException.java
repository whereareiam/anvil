package me.whereareiam.anvil.agent.api.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Failure raised while connecting to or communicating with a platform agent.
 */
public class AgentException extends RuntimeException {
	/**
	 * Creates an agent failure with a diagnostic message.
	 *
	 * @param message diagnostic message
	 */
	public AgentException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates an agent failure with its originating cause.
	 *
	 * @param message diagnostic message
	 * @param cause originating cause
	 */
	public AgentException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}
}
