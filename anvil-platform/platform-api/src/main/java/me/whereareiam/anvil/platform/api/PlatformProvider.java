package me.whereareiam.anvil.platform.api;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
	 * Writes platform configuration into a clean workspace.
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
	 * Returns the minimum Java runtime for the selected distribution.
	 *
	 * @param process server or proxy declaration
	 * @return Java feature version
	 */
	int minimumJavaVersion(@NotNull MinecraftProcess process);

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
	 * @return platform agent descriptor, or {@code null} when the provider exposes no agent
	 */
	default @Nullable PlatformAgentDescriptor platformAgent() {
		return null;
	}
}
