package me.whereareiam.anvil.api.model.java.local;

import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSource;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Supplies one already installed Java executable for local execution.
 */
@Value
public class LocalJavaExecutable implements JavaSource {
    @NotNull Path executable;
}
