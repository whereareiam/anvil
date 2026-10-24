package me.whereareiam.anvil.workbench.api.exception;

import me.whereareiam.anvil.api.exception.AnvilException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Reports that a workbench could not be reached or used: it is unknown, unreachable, refused the engine, speaks
 * another protocol version, or failed in a way the engine has no closer exception for.
 */
public class WorkbenchException extends AnvilException {
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a failure with a diagnostic message.
	 *
	 * @param message diagnostic message
	 */
	public WorkbenchException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates a failure retaining its cause.
	 *
	 * @param message diagnostic message
	 * @param cause underlying failure, or null when unavailable
	 */
	public WorkbenchException(@NotNull String message, @Nullable Throwable cause) {
		super(message, cause);
	}
}
