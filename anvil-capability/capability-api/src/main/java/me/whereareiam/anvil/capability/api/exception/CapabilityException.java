package me.whereareiam.anvil.capability.api.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Reports provider discovery, dependency, compatibility, or capability-resolution failures.
 */
public class CapabilityException extends RuntimeException {
	/**
	 * Creates a capability failure with a diagnostic message.
	 *
	 * @param message diagnostic message
	 */
	public CapabilityException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates a capability failure with its originating cause.
	 *
	 * @param message diagnostic message
	 * @param cause originating failure
	 */
	public CapabilityException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}
}
