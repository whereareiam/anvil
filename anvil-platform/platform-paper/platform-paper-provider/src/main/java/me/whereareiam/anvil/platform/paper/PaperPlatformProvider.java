package me.whereareiam.anvil.platform.paper;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
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
 * Supplies Paper provisioning, configuration, and launch requirements.
 */
public final class PaperPlatformProvider implements PlatformProvider {
	private static final Pattern READY = Pattern.compile("Done \\([^)]+\\)! For help");
	private final PaperDistributionResolver distributions = new PaperDistributionResolver();
	private final PaperConfiguration configuration = new PaperConfiguration();

	@Override
	public @NotNull String id() {
		return Platforms.PAPER;
	}

	@Override
	public @NotNull Class<? extends MinecraftProcess> configurationType() {
		return MinecraftServer.class;
	}

	@Override
	public @NotNull List<ForwardingMode> forwardingModes() {
		return List.of(ForwardingMode.MODERN, ForwardingMode.LEGACY, ForwardingMode.NONE);
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
		String version = server.getDistribution().isLocal()
				? server.getMinecraftVersion() : server.getDistribution().getVersion();
		return version != null && version.startsWith("26.") ? 25 : 21;
	}

	@Override
	public @NotNull List<String> programArguments(@NotNull MinecraftProcess process) {
		return List.of("nogui");
	}

	@Override
	public @NotNull List<WorkspaceCache> defaultCaches(@NotNull MinecraftProcess process) {
		return List.of(WorkspaceCache.builder().group("paper").path(Path.of("libraries")).build());
	}

	@Override
	public @NotNull PlatformAgentDescriptor platformAgent() {
		return PlatformAgentDescriptor.builder()
				.entrypointClassName("me.whereareiam.anvil.agent.bukkit.AnvilBukkitAgent")
				.build();
	}
}
