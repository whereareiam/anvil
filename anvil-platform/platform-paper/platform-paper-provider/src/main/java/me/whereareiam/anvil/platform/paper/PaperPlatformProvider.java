package me.whereareiam.anvil.platform.paper;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Supplies Paper provisioning, configuration, and launch requirements.
 * Java rows, the agent's minimum Java, verified versions and known versions live in
 * {@code paper-versions.toml}.
 */
public final class PaperPlatformProvider implements PlatformProvider {
	private static final Pattern READY = Pattern.compile("Done \\([^)]+\\)! For help");
	private static final URL VERSION_DATA = PaperPlatformProvider.class.getResource("paper-versions.toml");
	private static final MinecraftVersion LIBRARIES_SINCE = MinecraftVersion.parse("1.18");
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
		configuration.write((MinecraftServer) process, version(process), context);
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
	public @NotNull List<String> jvmArguments(@NotNull MinecraftProcess process, boolean consoleColors) {
		return consoleColors ? List.of("-Dterminal.ansi=true", "-Dterminal.jline=false") : List.of();
	}

	@Override
	public @NotNull List<String> programArguments(@NotNull MinecraftProcess process) {
		return List.of("nogui");
	}

	/**
	 * Returns Paperclip's caches: {@code cache} holds the downloaded Mojang server, and from 1.18
	 * Paperclip also extracts its {@code libraries}.
	 *
	 * @param process Paper server declaration
	 * @return cache declarations for the server's Minecraft version
	 */
	@Override
	public @NotNull List<WorkspaceCache> defaultCaches(@NotNull MinecraftProcess process) {
		WorkspaceCache cache = WorkspaceCache.builder().group("paper").path(Path.of("cache")).build();
		if (version(process).compareTo(LIBRARIES_SINCE) < 0) return List.of(cache);

		return List.of(WorkspaceCache.builder().group("paper").path(Path.of("libraries")).build(), cache);
	}

	private MinecraftVersion version(MinecraftProcess process) {
		MinecraftVersion version = platformVersion(process);
		if (version == null)
			throw new PlatformException("Paper server '" + process.getName() + "' declares no Minecraft version");

		return version;
	}

	@Override
	public @NotNull PlatformAgentDescriptor platformAgent() {
		return PlatformAgentDescriptor.builder()
				.entrypointClassName("me.whereareiam.anvil.agent.bukkit.AnvilBukkitAgent")
				.build();
	}
}
