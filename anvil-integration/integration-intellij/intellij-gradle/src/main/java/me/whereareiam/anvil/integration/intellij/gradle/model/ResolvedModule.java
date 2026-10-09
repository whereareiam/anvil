package me.whereareiam.anvil.integration.intellij.gradle.model;

import java.nio.file.Path;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;

/**
 * A resolved scenario source paired with its native Gradle preparation route.
 */
@Value
@Builder
public class ResolvedModule {
	@NotNull ScenarioSource source;
	@NotNull Path executionDirectory;
	@NotNull String preparationTaskPath;
}
