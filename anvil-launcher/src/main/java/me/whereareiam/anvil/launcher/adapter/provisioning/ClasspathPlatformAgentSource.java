package me.whereareiam.anvil.launcher.adapter.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.AgentArtifactLocator;
import me.whereareiam.anvil.capability.AgentExtensions;
import me.whereareiam.anvil.platform.api.PlatformAgentSource;
import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Finds a platform's agent, and the agent extensions the installed capabilities declare for the platform, on the
 * launcher's runtime class path.
 */
@RequiredArgsConstructor
public final class ClasspathPlatformAgentSource implements PlatformAgentSource {
	private final @NotNull AgentArtifactLocator locator;
	private final @NotNull AgentExtensions extensions;

	@Override
	public @NotNull Path locate(@NotNull PlatformAgentDescriptor agent) {
		return locator.locate(agent.getEntrypointClassName());
	}

	@Override
	public @NotNull List<Path> extensions(@NotNull String platformId) {
		return extensions.forPlatform(platformId).stream()
				.map(extension -> locator.locate(extension.getEntrypoint()))
				.distinct()
				.toList();
	}
}
