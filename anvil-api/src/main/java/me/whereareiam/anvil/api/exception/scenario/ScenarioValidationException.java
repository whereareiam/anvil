package me.whereareiam.anvil.api.exception.scenario;

import me.whereareiam.anvil.api.exception.AnvilException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Reports invalid scenario declarations or incompatible environment configuration.
 */
public class ScenarioValidationException extends AnvilException {
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a failure with a diagnostic message.
	 *
	 * @param message diagnostic message
	 */
	public ScenarioValidationException(@NotNull String message) {
		super(message);
	}

	/**
	 * Creates a failure retaining its original cause.
	 *
	 * @param message diagnostic message
	 * @param cause underlying failure, or null when unavailable
	 */
	public ScenarioValidationException(
			@NotNull String message,
			@Nullable Throwable cause
	) {
		super(message, cause);
	}
}
