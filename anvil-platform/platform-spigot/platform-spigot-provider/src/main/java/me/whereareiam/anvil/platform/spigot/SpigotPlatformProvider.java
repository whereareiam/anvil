package me.whereareiam.anvil.platform.spigot;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Supplies Spigot provisioning, configuration, and launch requirements.
 */
public final class SpigotPlatformProvider implements PlatformProvider {
	private static final Pattern READY = Pattern.compile("Done \\([^)]+\\)! For help");
	private final SpigotDistributionResolver distributions = new SpigotDistributionResolver();
	private final SpigotConfiguration configuration = new SpigotConfiguration();

	@Override
	public @NotNull String id() {
		return Platforms.SPIGOT;
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
	public @NotNull List<ForwardingMode> forwardingModes() {
		return List.of(ForwardingMode.LEGACY, ForwardingMode.NONE);
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
	public int minimumJavaVersion(@NotNull MinecraftProcess process) {
		MinecraftServer server = (MinecraftServer) process;
		String version = server.getMinecraftVersion() != null
				? server.getMinecraftVersion() : server.getDistribution().getVersion();

		if (version != null && version.matches("1\\.(18|19)(\\..*)?"))
			return 17;
		return version != null && version.startsWith("26.") ? 25 : 21;
	}

	@Override
	public @Nullable Integer maximumJavaVersion(@NotNull MinecraftProcess process) {
		MinecraftServer server = (MinecraftServer) process;
		String version = server.getMinecraftVersion() != null
				? server.getMinecraftVersion() : server.getDistribution().getVersion();
		if (version != null && version.matches("1\\.18(?:\\..*)?"))
			return 18;
		if (version != null && version.matches("1\\.19(?:\\..*)?"))
			return 20;
		return null;
	}

	@Override
	public @NotNull List<String> programArguments(@NotNull MinecraftProcess process) {
		return List.of("nogui");
	}

	@Override
	public @NotNull PlatformAgentDescriptor platformAgent() {
		return PlatformAgentDescriptor.builder()
				.entrypointClassName("me.whereareiam.anvil.agent.bukkit.AnvilBukkitAgent")
				.build();
	}
}
