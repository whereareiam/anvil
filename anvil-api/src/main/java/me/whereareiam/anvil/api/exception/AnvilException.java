package me.whereareiam.anvil.api.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Runtime failure raised while validating, provisioning, or running a scenario.
 */
public class AnvilException extends RuntimeException {
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a failure with a diagnostic message.
	 *
	 * @param message diagnostic message
	 */
	public AnvilException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates a failure with its root cause.
	 *
	 * @param message diagnostic message
	 * @param cause root cause, or null when unavailable
	 */
	public AnvilException(@NotNull String message, @Nullable Throwable cause) {
		super(message, cause);
	}
}
