package me.whereareiam.anvil.platform.neoforge;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Supplies NeoForge provisioning, configuration, and launch requirements.
 * A NeoForge server is installed once per release by its official installer and started through the
 * installer's server starter JAR. Java rows, the agent's minimum Java, verified versions and known
 * versions live in {@code neoforge-versions.toml}.
 */
public final class NeoForgePlatformProvider implements PlatformProvider {
	private static final Pattern READY = Pattern.compile("Done \\([^)]+\\)! For help");
	private static final URL VERSION_DATA = NeoForgePlatformProvider.class.getResource("neoforge-versions.toml");

	private final NeoForgeDistributionResolver distributions = new NeoForgeDistributionResolver();
	private final NeoForgeConfiguration configuration = new NeoForgeConfiguration();

	@Override
	public @NotNull String id() {
		return Platforms.NEOFORGE;
	}

	@Override
	public @NotNull Class<? extends MinecraftProcess> configurationType() {
		return MinecraftServer.class;
	}

	@Override
	public void validateDistribution(@NotNull MinecraftProcess process) {
		distributions.validate((MinecraftServer) process);
	}

	@Override
	public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
		return distributions.resolve((MinecraftServer) process, context);
	}

	@Override
	public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
		MinecraftServer server = (MinecraftServer) process;
		distributions.installation(server, context).linkInto(context.getWorkDirectory());
		configuration.write(server, context);
	}

	@Override
	public @NotNull Pattern readinessPattern() {
		return READY;
	}

	@Override
	public @NotNull URL versionData() {
		return VERSION_DATA;
	}

	@Override
	public @NotNull List<String> programArguments(@NotNull MinecraftProcess process) {
		return List.of("nogui");
	}

	@Override
	public @NotNull PlatformAgentDescriptor platformAgent() {
		return PlatformAgentDescriptor.builder()
				.entrypointClassName("me.whereareiam.anvil.agent.neoforge.AnvilNeoForgeAgent")
				.target(Path.of("mods", "anvil-platform-agent.jar"))
				.build();
	}
}
