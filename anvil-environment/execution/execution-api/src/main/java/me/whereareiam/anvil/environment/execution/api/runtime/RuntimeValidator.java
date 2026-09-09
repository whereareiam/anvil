package me.whereareiam.anvil.environment.execution.api.runtime;

import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import org.jetbrains.annotations.NotNull;

/**
 * Checks a runtime probed inside an execution environment against its process requirements.
 */

public interface RuntimeValidator {
	/**
	 * Validates the output of Java's {@code -XshowSettings:properties -version} probe.
	 *
	 * @param properties unmodified runtime probe output
	 * @param request process requirement and platform minimum
	 * @throws RuntimeException when the probe is invalid or the runtime is incompatible
	 */
	void validate(@NotNull String properties, @NotNull ProcessRequest request);
}
