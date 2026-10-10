package me.whereareiam.anvil.api.model.process;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.ProcessLifetime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
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
	 * Returns optional labels for tooling without changing the process name used for routing.
	 *
	 * @return presentation metadata, or null when none is supplied
	 */
	@Nullable PresentationMetadata getMetadata();

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
	 * Returns process-specific Java requirements and installation source.
	 * Omitted members inherit independently from the scenario and engine selections.
	 *
	 * @return declared Java selection
	 */
	@NotNull JavaSelection getJavaSelection();

	/**
	 * Returns whether the process authenticates players with Mojang services.
	 *
	 * @return whether online mode is enabled
	 */
	boolean isOnlineMode();

	/**
	 * Returns how long the process keeps running. A process with {@link ProcessLifetime#ENGINE} is started once
	 * and serves one scenario after another, so it carries its files, its world and whatever its plugins
	 * remember from one scenario to the next.
	 *
	 * @return declared lifetime
	 */
	@NotNull ProcessLifetime getLifetime();

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
	 * Returns the session server this process verifies online logins against instead of Mojang's.
	 * It is the base address whose {@code hasJoined} endpoint answers the verification, for example
	 * {@code http://127.0.0.1:25580/session/minecraft}. Planning refuses a platform that cannot be redirected.
	 *
	 * @return session server base address, or null to verify against Mojang
	 */
	@Nullable URI getSessionServer();

	/**
	 * Returns additional JVM arguments.
	 *
	 * @return JVM arguments
	 */
	@NotNull List<String> getJvmArguments();
}
