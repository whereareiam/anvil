package me.whereareiam.anvil.platform.bungeecord;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Supplies BungeeCord provisioning, configuration, and launch requirements.
 */
public final class BungeeCordPlatformProvider implements PlatformProvider {
	private static final Pattern READY = Pattern.compile("Listening on .+:[0-9]+");
	private final BungeeCordDistributionResolver distributions = new BungeeCordDistributionResolver();
	private final BungeeCordConfiguration configuration = new BungeeCordConfiguration();

	@Override
	public @NotNull String id() {
		return Platforms.BUNGEECORD;
	}

	@Override
	public @NotNull Class<? extends MinecraftProcess> configurationType() {
		return MinecraftProxy.class;
	}

	@Override
	public @NotNull List<ForwardingMode> forwardingModes() {
		return List.of(ForwardingMode.LEGACY);
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
	public @NotNull String stopCommand() {
		return "end";
	}

	@Override
	public @NotNull PlatformAgentDescriptor platformAgent() {
		return PlatformAgentDescriptor.builder()
				.entrypointClassName("me.whereareiam.anvil.agent.bungeecord.AnvilBungeeCordAgent")
				.build();
	}
}
