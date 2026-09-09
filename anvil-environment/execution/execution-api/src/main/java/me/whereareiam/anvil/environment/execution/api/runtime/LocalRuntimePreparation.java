package me.whereareiam.anvil.environment.execution.api.runtime;

import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Supplies a verified executable for a local process request.
 */

public interface LocalRuntimePreparation {
	/**
	 * Resolves the executable used by this process, including any provider-specific source fallback.
	 *
	 * @param request declared Java requirement and process constraints
	 * @param source selected explicit source or the local provider's configured fallback
	 * @return verified local Java executable
	 */
	@NotNull Path executable(@NotNull ProcessRequest request, @Nullable JavaSource source);
}
