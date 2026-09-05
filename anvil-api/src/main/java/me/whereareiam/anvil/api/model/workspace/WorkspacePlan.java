package me.whereareiam.anvil.api.model.workspace;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Independent asset, cache, and cleanup declarations for one process workspace.
 */
@Value
@Builder(toBuilder = true)
public class WorkspacePlan {
	@NotNull
	@Builder.Default
	WorkspaceMode mode = WorkspaceMode.FRESH;

	@NotNull
	@Singular("asset")
	List<WorkspaceAsset> assets;

	@NotNull
	@Singular("cache")
	List<WorkspaceCache> caches;

	@NotNull
	@Singular("cleanup")
	List<WorkspaceCleanup> cleanups;
}
