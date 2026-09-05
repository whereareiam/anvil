package me.whereareiam.anvil.api.exception.scenario;

import lombok.Getter;
import me.whereareiam.anvil.api.exception.AnvilException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Reports failure to assemble a scenario or execute its setup hook.
 */
@Getter
public class ScenarioStartupException extends AnvilException {
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Name of the affected scenario.
	 */
	private final @NotNull String scenarioName;

	/**
	 * Creates a failure with a diagnostic message.
	 *
	 * @param scenarioName affected scenario name
	 * @param message diagnostic message
	 */
	public ScenarioStartupException(@NotNull String scenarioName, @NotNull String message) {
		super(message);
		this.scenarioName = scenarioName;
	}

	/**
	 * Creates a failure retaining its original cause.
	 *
	 * @param scenarioName affected scenario name
	 * @param message diagnostic message
	 * @param cause underlying failure, or null when unavailable
	 */
	public ScenarioStartupException(
			@NotNull String scenarioName,
			@NotNull String message,
			@Nullable Throwable cause
	) {
		super(message, cause);
		this.scenarioName = scenarioName;
	}
}
