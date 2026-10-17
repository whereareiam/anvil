package me.whereareiam.anvil.platform.api.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Indicates that a platform distribution could not be resolved or configured.
 */
public class PlatformException extends RuntimeException {
	/**
	 * Creates a platform failure with a diagnostic message.
	 *
	 * @param message diagnostic message
	 */
	public PlatformException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates a platform failure with its underlying cause.
	 *
	 * @param message diagnostic message
	 * @param cause underlying failure
	 */
	public PlatformException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}
}
