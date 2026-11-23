package me.whereareiam.anvil.engine.config;

import com.sun.management.OperatingSystemMXBean;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import me.whereareiam.anvil.api.type.ProcessPriority;
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
		ProcessScheduling scheduling = options.processScheduling();

		return options.toBuilder()
				.cacheDirectory(options.cacheDirectory() == null ? cacheDirectory() : options.cacheDirectory())
				.accountsDirectory(options.accountsDirectory() == null ? accountsDirectory() : options.accountsDirectory())
				.processTimeouts(options.processTimeouts().withDefaults(EngineOptions.builder().build().processTimeouts()))
				.processScheduling(scheduling.toBuilder()
						.parallelism(scheduling.parallelism() == null ? detectedParallelism() : scheduling.parallelism())
						.startupMemoryMegabytes(scheduling.startupMemoryMegabytes() == null
								? detectedStartupMemory() : scheduling.startupMemoryMegabytes())
						.priority(scheduling.priority() == null ? ProcessPriority.NORMAL : scheduling.priority())
						.build())
				.downloadParallelism(options.downloadParallelism() == null
						? detectedDownloadParallelism() : options.downloadParallelism())
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

	/** Returns the default user-local authenticated account directory. */
	public static @NotNull Path accountsDirectory() {
		return cacheDirectory().resolve("accounts");
	}

}
