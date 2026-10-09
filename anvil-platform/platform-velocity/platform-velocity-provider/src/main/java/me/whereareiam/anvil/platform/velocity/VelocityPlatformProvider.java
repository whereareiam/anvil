package me.whereareiam.anvil.platform.velocity;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supplies Velocity provisioning, configuration, and launch requirements.
 * Java rows, the agent's minimum Java, verified releases and known releases live in
 * {@code velocity-versions.toml}, keyed by Velocity release.
 */
public final class VelocityPlatformProvider implements PlatformProvider {
	private static final Pattern READY = Pattern.compile("Done \\([^)]+\\)!");
	private static final Pattern RELEASE = Pattern.compile("(\\d+(?:\\.\\d+){1,2})(?:-[A-Za-z0-9.]+)?");
	private static final URL VERSION_DATA = VelocityPlatformProvider.class.getResource("velocity-versions.toml");
	private final VelocityDistributionResolver distributions = new VelocityDistributionResolver();
	private final VelocityConfiguration configuration = new VelocityConfiguration();

	@Override
	public @NotNull String id() {
		return Platforms.VELOCITY;
	}

	@Override
	public @NotNull Class<? extends MinecraftProcess> configurationType() {
		return MinecraftProxy.class;
	}

	@Override
	public @NotNull List<ForwardingMode> forwardingModes() {
		return List.of(ForwardingMode.MODERN, ForwardingMode.LEGACY);
	}

	@Override
	public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
		return distributions.resolve((MinecraftProxy) process, context);
	}

	@Override
	public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
		configuration.write((MinecraftProxy) process, context);
	}

	@Override
	public @NotNull Pattern readinessPattern() {
		return READY;
	}

	@Override
	public @NotNull URL versionData() {
		return VERSION_DATA;
	}

	/**
	 * Returns the Velocity release of a remote distribution, ignoring a qualifier such as
	 * {@code -SNAPSHOT}. Local and artifact JARs carry no release and use the newest Java row.
	 *
	 * @param process Velocity proxy declaration
	 * @return Velocity release, or null for local and artifact JARs
	 * @throws PlatformException when the remote version is not a Velocity release
	 */
	@Override
	public @Nullable MinecraftVersion platformVersion(@NotNull MinecraftProcess process) {
		var distribution = process.getDistribution();
		if (distribution.isLocal() || distribution.isArtifact() || distribution.getVersion() == null) return null;

		Matcher release = RELEASE.matcher(distribution.getVersion());
		if (!release.matches())
			throw new PlatformException("Velocity version '" + distribution.getVersion() + "' is not a Velocity release");

		return MinecraftVersion.parse(release.group(1));
	}

	@Override
	public @NotNull List<WorkspaceCache> defaultCaches(@NotNull MinecraftProcess process) {
		return List.of(WorkspaceCache.builder().group("velocity").path(Path.of("libraries")).build());
	}

	@Override
	public @NotNull List<String> jvmArguments(@NotNull MinecraftProcess process, boolean consoleColors) {
		return consoleColors ? List.of("-Dterminal.ansi=true", "-Dterminal.jline=false") : List.of();
	}

	@Override
	public @NotNull String stopCommand() {
		return "shutdown";
	}

	@Override
	public @NotNull PlatformAgentDescriptor platformAgent() {
		return PlatformAgentDescriptor.builder()
				.entrypointClassName("me.whereareiam.anvil.agent.velocity.AnvilVelocityAgent")
				.build();
	}
}
