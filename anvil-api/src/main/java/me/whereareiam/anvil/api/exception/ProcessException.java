package me.whereareiam.anvil.api.exception;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Reports a failure while starting, communicating with, or stopping a process.
 */
@Getter
public class ProcessException extends AnvilException {
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Name of the affected process.
	 */
	private final @NotNull String processName;

	/**
	 * Creates a failure with a diagnostic message.
	 *
	 * @param processName affected process name
	 * @param message diagnostic message
	 */
	public ProcessException(@NotNull String processName, @NotNull String message) {
		super(message);
		this.processName = processName;
	}

	/**
	 * Creates a failure retaining its original cause.
	 *
	 * @param processName affected process name
	 * @param message diagnostic message
	 * @param cause underlying failure, or null when unavailable
	 */
	public ProcessException(
			@NotNull String processName,
			@NotNull String message,
			@Nullable Throwable cause
	) {
		super(message, cause);
		this.processName = processName;
	}
}
