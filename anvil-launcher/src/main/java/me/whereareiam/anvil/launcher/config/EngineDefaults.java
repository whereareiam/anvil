package me.whereareiam.anvil.launcher.config;

import com.sun.management.OperatingSystemMXBean;
import me.whereareiam.anvil.api.model.EngineOptions;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
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
				.parallelism(options.getParallelism() == null ? detectedParallelism() : options.getParallelism())
				.startupMemoryMegabytes(options.getStartupMemoryMegabytes() == null
						? detectedStartupMemory()
						: options.getStartupMemoryMegabytes())
				.downloadParallelism(options.getDownloadParallelism() == null
						? detectedDownloadParallelism()
						: options.getDownloadParallelism())
				.build();
	}

	private static int detectedParallelism() {
		return Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 8);
	}

	private static int detectedDownloadParallelism() {
		return Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 8);
	}

	private static int detectedStartupMemory() {
		long megabytes = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize()
				/ (1024 * 1024);
		return (int) Math.clamp(megabytes / 2, 1024, 8192);
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
