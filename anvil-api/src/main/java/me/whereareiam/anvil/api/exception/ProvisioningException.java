package me.whereareiam.anvil.api.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Reports failure to prepare artifacts, Java installations, or workspaces.
 */
public class ProvisioningException extends AnvilException {
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a failure with a diagnostic message.
	 *
	 * @param message diagnostic message
	 */
	public ProvisioningException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates a failure retaining its original cause.
	 *
	 * @param message diagnostic message
	 * @param cause underlying failure, or null when unavailable
	 */
	public ProvisioningException(
			@NotNull String message,
			@Nullable Throwable cause
	) {
		super(message, cause);
	}
}
