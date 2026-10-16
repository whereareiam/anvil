package me.whereareiam.anvil.platform.lumi;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.network.ListenerTransport;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Supplies Lumi provisioning, configuration, and launch requirements.
 * Lumi is a Bedrock server: its game listener accepts players over UDP, and it serves exactly the protocol of the
 * server's Minecraft version. Java rows, the agent's minimum Java, verified and known versions, the protocol of
 * each version and the versions of each Lumi release live in {@code lumi-versions.toml}.
 */
public final class LumiPlatformProvider implements PlatformProvider {
	private static final Pattern READY = Pattern.compile("Done \\([^)]+\\)! For help");
	private static final URL VERSION_DATA = LumiPlatformProvider.class.getResource("lumi-versions.toml");

	private final LumiReleases releases = new LumiReleases(VERSION_DATA);
	private final LumiDistributionResolver distributions = new LumiDistributionResolver(releases);
	private final LumiConfiguration configuration = new LumiConfiguration(releases);

	@Override
	public @NotNull String id() {
		return Platforms.LUMI;
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
	public @NotNull ListenerTransport gameTransport() {
		return ListenerTransport.UDP;
	}

	@Override
	public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
		return distributions.resolve((MinecraftServer) process, context);
	}

	@Override
	public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) throws IOException {
		configuration.write((MinecraftServer) process, context);
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
	public @NotNull PlatformAgentDescriptor platformAgent() {
		return PlatformAgentDescriptor.builder()
				.entrypointClassName("me.whereareiam.anvil.agent.lumi.AnvilLumiAgent")
				.target(Path.of("plugins", "anvil-platform-agent.jar"))
				.build();
	}
}
