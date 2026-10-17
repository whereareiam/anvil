package me.whereareiam.anvil.capability.api.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Reports that a request channel cannot send a request now, for example while its process restarts and the agent
 * connection is replaced. A later request may succeed; a failure of the operation itself is reported differently.
 */
public class ChannelUnavailableException extends CapabilityException {
	/**
	 * Creates the failure with its originating cause.
	 *
	 * @param message diagnostic message
	 * @param cause originating failure
	 */
	public ChannelUnavailableException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}
}
