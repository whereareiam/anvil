package me.whereareiam.anvil.api.model.process;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;

/**
 * Shared immutable configuration exposed to platform providers for one local Minecraft process.
 */
public interface MinecraftProcess {
	/**
	 * Returns the scenario-unique process name.
	 *
	 * @return process name
	 */
	@NotNull String getName();

	/**
	 * Returns the platform-provider identifier.
	 *
	 * @return platform identifier
	 */
	@NotNull String getPlatform();

	/**
	 * Returns the executable distribution selection.
	 *
	 * @return selected distribution
	 */
	@NotNull Distribution getDistribution();

	/**
	 * Returns whether the process authenticates players with Mojang services.
	 *
	 * @return whether online mode is enabled
	 */
	boolean isOnlineMode();

	/**
	 * Returns the maximum heap size in MiB.
	 *
	 * @return maximum heap size
	 */
	int getMemoryMegabytes();

	/**
	 * Returns independent asset, cache, and cleanup declarations for the process workspace.
	 *
	 * @return workspace plan
	 */
	@NotNull WorkspacePlan getWorkspace();

	/**
	 * Returns platform settings applied before Anvil-owned runtime values.
	 *
	 * @return platform settings
	 */
	@NotNull Map<String, String> getSettings();

	/**
	 * Returns additional JVM arguments.
	 *
	 * @return JVM arguments
	 */
	@NotNull List<String> getJvmArguments();
}
