package me.whereareiam.anvil.execution.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSource;
import org.jetbrains.annotations.NotNull;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Inputs required to prepare a process location before platform configuration.
 */
@Value
@Builder(toBuilder = true)
public class ProcessRequest {
    @NotNull String name;
    @NotNull Path workspace;
    @NotNull JavaRequirement javaRequirement;
    @Nullable JavaSource javaSource;
    int minimumJavaVersion;
    boolean agent;
    @Builder.Default
    boolean publishGame = true;
}
