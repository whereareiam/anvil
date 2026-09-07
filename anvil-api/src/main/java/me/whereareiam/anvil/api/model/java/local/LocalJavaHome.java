package me.whereareiam.anvil.api.model.java.local;

import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaSource;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/** Supplies an already installed JDK home for local execution. */
@Value
public class LocalJavaHome implements JavaSource {
	@NotNull Path home;
}
