package me.whereareiam.anvil.execution.api.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import me.whereareiam.anvil.provisioning.api.JavaRuntimeValidator;

import java.nio.file.Path;

import me.whereareiam.anvil.provisioning.api.artifact.ArtifactStore;
import me.whereareiam.anvil.api.model.NetworkPolicy;

/**
 * Host resources and runtime validation policy for a scenario execution environment.
 */
@Value
@Builder(toBuilder = true)
public class ExecutionContext {
    @NotNull Path cacheDirectory;
    @NotNull String bindAddress;
    @NotNull JavaRuntimeValidator javaValidator;
    @NotNull ArtifactStore artifacts;
    boolean offline;
    boolean refresh;
    @NotNull
    @Builder.Default
    NetworkPolicy networkPolicy = NetworkPolicy.builder().build();
}
