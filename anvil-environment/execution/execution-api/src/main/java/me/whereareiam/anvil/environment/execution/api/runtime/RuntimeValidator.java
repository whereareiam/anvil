package me.whereareiam.anvil.environment.execution.api.runtime;

import me.whereareiam.anvil.api.exception.JavaVersionMismatchException;
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
	 * @param request process whose planned requirement names the exact Java feature version
	 * @throws JavaVersionMismatchException when the runtime is another Java feature version
	 * @throws RuntimeException when the probe cannot be read or a requested release or distribution does not
	 * match
	 */
	void validate(@NotNull String properties, @NotNull ProcessRequest request);
}
