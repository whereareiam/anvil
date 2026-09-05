package me.whereareiam.anvil.api.model.workspace;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.CleanupPhase;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Deletes one workspace-relative file or directory at an explicit lifecycle phase.
 */
@Value
@Builder(toBuilder = true)
public class WorkspaceCleanup {
	@NotNull
	@Builder.Default
	String group = "default";

	@NotNull Path path;

	@NotNull
	@Builder.Default
	CleanupPhase phase = CleanupPhase.BEFORE_START;
}
