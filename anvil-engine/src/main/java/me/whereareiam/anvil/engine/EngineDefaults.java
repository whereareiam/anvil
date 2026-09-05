package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.engine.provisioning.java.JavaExecutables;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Resolves environment-dependent defaults for local engine configuration.
 */
public final class EngineDefaults {
	/**
	 * Resolves omitted environment-dependent paths without changing the supplied API model.
	 *
	 * @param options declarative options
	 * @return options containing the effective local paths
	 */
	public static @NotNull EngineOptions resolve(@NotNull EngineOptions options) {
		return options.toBuilder()
				.cacheDirectory(options.getCacheDirectory() == null ? cacheDirectory() : options.getCacheDirectory())
				.defaultJavaExecutable(options.getDefaultJavaExecutable() == null ? JavaExecutables.current() : options.getDefaultJavaExecutable())
				.build();
	}

	/**
	 * Returns the shared cache below the current user's home directory.
	 *
	 * @return default cache directory
	 */
	public static @NotNull Path cacheDirectory() {
		return Path.of(System.getProperty("user.home"), ".anvil");
	}

}
