package me.whereareiam.anvil.platform.api;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service-provider contract for provisioning and launching a Minecraft distribution.
 */
public interface PlatformProvider {
	/**
	 * Returns the stable platform identifier used by scenario servers or proxies.
	 *
	 * @return platform identifier
	 */
	@NotNull String id();

	/**
	 * Returns the configuration type supported by this provider.
	 *
	 * @return supported process configuration type
	 */
	@NotNull Class<? extends MinecraftProcess> configurationType();

	/**
	 * Validates provider-owned distribution selectors before any process is launched.
	 * This method performs no downloads or process execution. The default requires a remote build
	 * identifier; providers using content-pinned artifacts override it to validate their checksum.
	 *
	 * @param process server or proxy declaration with a distribution
	 * @throws PlatformException when the selected distribution is invalid for this provider
	 */
	default void validateDistribution(@NotNull MinecraftProcess process) {
		var distribution = process.getDistribution();
		if (distribution.isLocal() || distribution.isArtifact())
			return;
		if (distribution.getBuild() == null || distribution.getBuild().isBlank())
			throw new PlatformException("Remote distribution requires an immutable build identifier");
	}

	/**
	 * Returns supported identity-forwarding protocols in preference order. Servers declare what
	 * they accept; proxies declare what they send. Every member of a connected proxy/server group
	 * must share a mode, so a server exposed by several proxies receives one consistent configuration.
	 *
	 * @return supported modes in preference order
	 */
	default @NotNull List<ForwardingMode> forwardingModes() {
		return List.of(ForwardingMode.NONE);
	}

	/**
	 * Resolves or builds the process executable JAR.
	 *
	 * @param process server or proxy declaration
	 * @param context provisioning context
	 * @return resolved distribution
	 * @throws IOException when the artifact cannot be prepared
	 */
	@NotNull ResolvedDistribution resolve(
			@NotNull MinecraftProcess process,
			@NotNull PlatformContext context
	) throws IOException;

	/**
	 * Applies runtime settings to the prepared workspace before each process start, including restarts.
	 * Preserve unrelated configuration and restrict writes to this process's workspace.
	 * Independent processes may be configured concurrently using the same provider instance.
	 *
	 * @param process server or proxy declaration
	 * @param context provisioning context
	 * @throws IOException when configuration cannot be written
	 */
	void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException;

	/**
	 * Returns the log expression that marks this platform ready for players.
	 *
	 * @return readiness expression
	 */
	@NotNull Pattern readinessPattern();

	/**
	 * Returns this platform's version data: a TOML resource, by convention
	 * {@code <platform>-versions.toml} beside the provider class. Planning reads it once per engine,
	 * selects the exact Java version of each process from it, and assesses the process's
	 * {@link SupportLevel} before anything is downloaded or launched.
	 *
	 * <pre>{@code
	 * # Platform versions the data declares as supported (COMPATIBLE).
	 * known = ["1.16.5", "1.17.1", "1.18.2"]
	 *
	 * # Oldest Java the agent from platformAgent() runs on. Required when the provider installs an
	 * # agent; planning never selects older Java for the platform.
	 * [agent]
	 * minimumJava = 11
	 *
	 * # Platform version -> Java feature versions Anvil's live matrix runs it on (VERIFIED).
	 * [verified]
	 * "1.16.5" = [11, 17]
	 *
	 * # Each row applies from `since` until the next row starts. Only the first row may omit
	 * # `since`; it then applies from the oldest version. A maximum is the platform's own refusal.
	 * [[java]]
	 * since = "1.16.5"
	 * minimum = 11
	 * maximum = 16
	 * preferred = 11
	 * maximumBypassProperty = "Paper.IgnoreJavaVersion"
	 * }</pre>
	 *
	 * <p>Platform versions are the values {@link #platformVersion(MinecraftProcess)} returns; a process
	 * without a version uses the newest row. Each preferred version is an LTS release (11, 17, 21, 25,
	 * then every fourth release) inside its row. A bypass property needs a maximum, and planning sets
	 * it only for an explicitly requested Java version above that maximum. Unknown keys are refused.</p>
	 *
	 * <pre>{@code
	 * public @NotNull URL versionData() {
	 *     return ExamplePlatformProvider.class.getResource("example-versions.toml");
	 * }
	 * }</pre>
	 *
	 * @return location of this provider's version data resource
	 */
	@NotNull URL versionData();

	/**
	 * Returns the version that keys this process in {@link #versionData()}. The default returns a
	 * server's {@link MinecraftServer#nativeVersion() native Minecraft version} and null for
	 * proxies. A proxy whose data is keyed by its own release overrides it.
	 *
	 * @param process server or proxy declaration
	 * @return platform version, or null when the distribution carries none; planning then uses the
	 * newest Java row and does not assess the version
	 * @throws PlatformException when the declared version is not a release version
	 */
	default @Nullable MinecraftVersion platformVersion(@NotNull MinecraftProcess process) {
		if (!(process instanceof MinecraftServer server)) return null;

		try {
			return server.nativeVersion();
		} catch (IllegalArgumentException invalid) {
			throw new PlatformException(invalid.getMessage(), invalid);
		}
	}

	/**
	 * Returns JVM defaults selected by this platform while planning a process launch.
	 * The engine places these arguments before the process declaration's explicit JVM arguments,
	 * so an explicit process system property overrides a provider default for the same property.
	 * Implementations must leave the process declaration unchanged. The default supplies no arguments.
	 *
	 * @param process server or proxy declaration
	 * @param consoleColors whether the output consumer requests ANSI colors; false preserves platform defaults
	 * @return immutable ordered provider JVM defaults
	 */
	default @NotNull List<String> jvmArguments(@NotNull MinecraftProcess process, boolean consoleColors) {
		return List.of();
	}

	/**
	 * Returns arguments appended after the executable JAR.
	 *
	 * @param process server or proxy declaration
	 * @return immutable program arguments
	 */
	default @NotNull List<String> programArguments(@NotNull MinecraftProcess process) {
		return List.of();
	}

	/**
	 * Returns cache paths normally produced by this platform in a process workspace.
	 *
	 * <p>These declarations are defaults only. A scenario may add another cache, replace a default
	 * by declaring the same path, or disable it with {@link CachePolicy#DISABLED}.
	 * Providers should declare only paths that are safe to reuse between compatible distributions.</p>
	 *
	 * @param process server or proxy declaration
	 * @return immutable provider cache declarations
	 */
	default @NotNull List<WorkspaceCache> defaultCaches(@NotNull MinecraftProcess process) {
		return List.of();
	}

	/**
	 * Returns the platform console command used for graceful shutdown.
	 *
	 * @return stop command
	 */
	default @NotNull String stopCommand() {
		return "stop";
	}

	/**
	 * Returns the optional in-process platform agent installed into the managed workspace.
	 *
	 * <p>A provider that returns an agent must declare the oldest Java the agent runs on as
	 * {@code [agent] minimumJava} in its {@link #versionData()}; planning refuses its processes before
	 * launch otherwise, and never selects older Java for them. Keep the declaration equal to the Java
	 * release the agent's classes target: planning trusts it, so a lower value lets a process start on
	 * Java its agent cannot load.</p>
	 *
	 * @return platform agent descriptor, or {@code null} when the provider exposes no agent
	 */
	default @Nullable PlatformAgentDescriptor platformAgent() {
		return null;
	}
}
