package me.whereareiam.anvil.engine.config;

import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import com.sun.management.OperatingSystemMXBean;
import me.whereareiam.anvil.api.model.EngineOptions;
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
		ProcessScheduling scheduling = options.getProcessScheduling();

		return options.toBuilder()
				.cacheDirectory(options.getCacheDirectory() == null ? cacheDirectory() : options.getCacheDirectory())
				.accountsDirectory(options.getAccountsDirectory() == null ? accountsDirectory() : options.getAccountsDirectory())
				.processTimeouts(options.getProcessTimeouts().withDefaults(EngineOptions.builder().build().getProcessTimeouts()))
				.processScheduling(scheduling.toBuilder()
						.parallelism(scheduling.getParallelism() == null ? detectedParallelism() : scheduling.getParallelism())
						.startupMemoryMegabytes(scheduling.getStartupMemoryMegabytes() == null
								? detectedStartupMemory() : scheduling.getStartupMemoryMegabytes())
						.priority(scheduling.getPriority() == null ? ProcessPriority.NORMAL : scheduling.getPriority())
						.build())
				.downloadParallelism(options.getDownloadParallelism() == null
						? detectedDownloadParallelism() : options.getDownloadParallelism())
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
