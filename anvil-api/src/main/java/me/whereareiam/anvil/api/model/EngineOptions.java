package me.whereareiam.anvil.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
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
     * Uses only previously acquired artifacts and resolution metadata.
     */
    boolean offline;

    /**
     * Resolves moving vendor selectors again, retaining newly selected immutable identities.
     */
    boolean refresh;

    /**
     * Maximum number of independent preparation or startup operations running together.
     */
    @Nullable Integer parallelism;

    /**
     * Combined declared heaps permitted to be starting at once, in MiB.
     */
    @Nullable Integer startupMemoryMegabytes;

    /**
     * Maximum simultaneous artifact transfers.
     */
    @Nullable Integer downloadParallelism;

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
    boolean downloadJava = true;

    /**
     * Grace period before escalating process termination.
     */
    @NotNull
    @Builder.Default
    Duration stopTimeout = Duration.ofSeconds(15);

    /**
     * Default process Java selection, overridden by a scenario or process declaration.
     */
    @NotNull
    @Builder.Default
    JavaRequirement javaRequirement = JavaRequirement.builder().build();

    /**
     * Default explicit Java source, overridden by a scenario or process source.
     */
    @Nullable
    JavaSource javaSource;

    /**
     * Default execution provider, overridden by a scenario declaration.
     */
    @NotNull
    @Builder.Default
    String executionId = "local";

    /**
     * Named local artifacts referenced by scenario declarations.
     */
    @NotNull
    @Singular("artifact")
    Map<String, Path> artifacts;
}
