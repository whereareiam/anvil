package me.whereareiam.anvil.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessScheduling;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Immutable engine configuration shared by direct embedding, JUnit, and the foreground runner.
 * Process deadlines are defaults that a scenario may override independently.
 */
@Value
@Builder(toBuilder = true)
public class EngineOptions {
	/**
	 * Default execution provider, overridden by a scenario declaration.
	 */
	@NotNull
	@Builder.Default
	String executionProviderId = "local";

	/**
	 * Selected protocol-provider identifier, or null to select the sole installed provider.
	 */
	@Nullable String protocolId;

	/**
	 * Default Java selection; requirements and installation source inherit independently.
	 */
	@NotNull
	@Builder.Default
	JavaSelection javaSelection = JavaSelection.builder().requirement(JavaRequirement.builder().build()).build();

	/**
	 * Default per-process deadlines; scenarios may override startup and shutdown separately.
	 */
	@NotNull
	@Builder.Default
	ProcessTimeouts processTimeouts = ProcessTimeouts.builder()
			.startup(Duration.ofMinutes(2))
			.shutdown(Duration.ofSeconds(15))
			.build();

	/**
	 * Concurrency and startup memory limits for each scenario operation.
	 */
	@NotNull
	@Builder.Default
	ProcessScheduling processScheduling = ProcessScheduling.builder().build();

	/**
	 * Explicit acceptance of the Minecraft EULA.
	 */
	@Builder.Default
	boolean eulaAccepted = false;

	/**
	 * Shared download and workspace cache directory, or null to use the current user's default cache.
	 */
	@Nullable Path cacheDirectory;

	/**
	 * Local account store directory. This is user-local state and is never part of a scenario definition.
	 */
	@Nullable Path accountsDirectory;

	/**
	 * Root for generated scenario workspaces.
	 */
	@NotNull
	@Builder.Default
	Path workDirectory = Path.of("build", "anvil");

	/**
	 * Retains diagnostic workspaces when scenario startup or execution fails.
	 */
	@Builder.Default
	boolean keepFailedWorkspaces = true;

	/**
	 * Named local artifacts referenced by scenario declarations.
	 */
	@NotNull
	@Singular("artifact")
	Map<String, Path> artifacts;
	/**
	 * Uses only previously acquired artifacts and resolution metadata.
	 */
	boolean offline;

	/**
	 * Resolves moving vendor selectors again, retaining newly selected immutable identities.
	 */
	boolean refresh;

	/**
	 * Permits provisioning Java when no suitable configured installation is available.
	 */
	@Builder.Default
	boolean downloadJava = true;

	/**
	 * Maximum simultaneous artifact transfers.
	 */
	@Nullable Integer downloadParallelism;

	/**
	 * Requests ANSI-colored console output from supporting platform providers. The default is false,
	 * which leaves platform output defaults unchanged; it does not strip colors already emitted.
	 * This capability does not request a terminal or interactive line editing.
	 */
	boolean consoleColors;

}
