package me.whereareiam.anvil.api.model.process;

import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
	 * Returns process-specific Java requirements, or null to inherit scenario defaults.
	 *
	 * @return process Java requirement
	 */
	@Nullable JavaRequirement getJavaRequirement();

	/**
	 * Returns the process Java source, or null to inherit scenario and engine defaults.
	 *
	 * @return process Java source
	 */
	@Nullable JavaSource getJavaSource();

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
