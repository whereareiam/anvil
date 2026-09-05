package me.whereareiam.anvil.platform.velocity;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Supplies Velocity provisioning, configuration, and launch requirements.
 */
public final class VelocityPlatformProvider implements PlatformProvider {
	private static final Pattern READY = Pattern.compile("Done \\([^)]+\\)!");
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
	public int minimumJavaVersion(@NotNull MinecraftProcess process) {
		return 21;
	}

	@Override
	public @NotNull List<WorkspaceCache> defaultCaches(@NotNull MinecraftProcess process) {
		return List.of(WorkspaceCache.builder().group("velocity").path(Path.of("libraries")).build());
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
