package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Internal platform-process agent installed into a managed server or proxy workspace.
 *
 * <p>The agent runs inside the selected platform JVM while the provider itself runs inside the
 * Anvil launcher JVM. Keeping the descriptor in the platform contract lets the launcher install
 * the correct shaded artifact without exposing agent implementation details to consumers.</p>
 */
@Value
@Builder
public class PlatformAgentDescriptor {
	/**
	 * Entry-point class used to locate the published agent artifact.
	 */
	@NotNull String entrypointClassName;

	/**
	 * Workspace-relative installation target.
	 */
	@NotNull
	@Builder.Default
	Path target = Path.of("plugins", "anvil-platform-agent.jar");
}
