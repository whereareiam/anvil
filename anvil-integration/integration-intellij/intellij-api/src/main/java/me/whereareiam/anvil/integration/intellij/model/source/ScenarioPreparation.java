package me.whereareiam.anvil.integration.intellij.model.source;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Executable instructions that prepare a scenario source's caller-owned tooling manifest.
 */
@Value
@Builder
public class ScenarioPreparation {
	@NotNull List<String> command;
	@NotNull Path workingDirectory;
	@NotNull Path manifestPath;
	/**
	 * Variables added to the inherited environment, such as the build tool's Java home.
	 */
	@Builder.Default
	@NotNull Map<String, String> environment = Map.of();
}
