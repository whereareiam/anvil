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
	 * @param request process whose planned requirement names the exact Java feature version
	 * @param source selected explicit source or the local provider's configured source for that version
	 * @return verified local Java executable of exactly that feature version
	 */
	@NotNull Path executable(@NotNull ProcessRequest request, @Nullable JavaSource source);
}
