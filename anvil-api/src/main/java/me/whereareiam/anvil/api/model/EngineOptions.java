package me.whereareiam.anvil.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Immutable engine configuration shared by direct embedding, JUnit, and the foreground runner.
 * Process startup deadlines are declared by the scenario, rather than by this configuration.
 */
@Value
@Builder(toBuilder = true)
public class EngineOptions {
	/**
	 * Selected protocol-provider identifier, or null to select the sole installed provider.
	 */
	@Nullable String protocolId;

	/**
	 * Shared download and workspace cache directory, or null to use the current user's default cache.
	 */
	@Nullable Path cacheDirectory;

	/**
	 * Root for generated scenario workspaces.
	 */
	@NotNull
	@Builder.Default
	Path workDirectory = Path.of("build", "anvil");

	/**
	 * Explicit acceptance of the Minecraft EULA.
	 */
	@Builder.Default
	boolean eulaAccepted = false;

	/**
	 * Retains diagnostic workspaces when scenario startup or execution fails.
	 */
	@Builder.Default
	boolean keepFailedWorkspaces = true;

	/**
	 * Permits provisioning Java when no suitable configured installation is available.
	 */
	@Builder.Default
	boolean autoDownloadJavaRuntimes = true;

	/**
	 * Grace period before escalating process termination.
	 */
	@NotNull
	@Builder.Default
	Duration stopTimeout = Duration.ofSeconds(15);

	/**
	 * Executable for the current Java installation, or null to use its default executable.
	 * Feature-version overrides take priority.
	 */
	@Nullable Path defaultJavaExecutable;

	/**
	 * Explicit Java executable paths indexed by feature version.
	 */
	@NotNull
	@Singular("javaExecutable")
	Map<Integer, Path> javaExecutables;

	/**
	 * Named local artifacts referenced by scenario declarations.
	 */
	@NotNull
	@Singular("artifact")
	Map<String, Path> artifacts;
}
