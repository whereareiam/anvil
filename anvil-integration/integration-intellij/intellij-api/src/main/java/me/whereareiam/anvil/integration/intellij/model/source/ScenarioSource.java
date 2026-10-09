package me.whereareiam.anvil.integration.intellij.model.source;

import java.nio.file.Path;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Imported project source that exposes Anvil scenario tooling.
 */
@Value
@Builder(toBuilder = true)
public class ScenarioSource {
	@NotNull String id;
	@NotNull String displayName;
	@NotNull String integrationId;
	@NotNull Path directory;
	long importRevision;
}
