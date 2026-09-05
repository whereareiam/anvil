package me.whereareiam.anvil.runner.model.command;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parsed startup arguments for one foreground runner invocation.
 */
@Value
@Builder
public class RunnerArguments {
    @NotNull
    String provider;
    @Builder.Default
    boolean list = false;
    @Nullable
    String scenario;
    @Nullable
    String group;
}
