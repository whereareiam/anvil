package me.whereareiam.anvil.execution.docker.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Immutable Docker container specification assembled by a process target.
 */
@Value
@Builder
public class ContainerConfiguration {
    @NotNull String name;
    @NotNull String network;
    @NotNull String alias;
    @NotNull String image;

    @NotNull Path workspace;
    @NotNull Path jar;

	@NotNull PortBindings ports;

    @Singular("argument")
    List<String> command;

    @Singular("environment")
    Map<String, String> environment;
}
