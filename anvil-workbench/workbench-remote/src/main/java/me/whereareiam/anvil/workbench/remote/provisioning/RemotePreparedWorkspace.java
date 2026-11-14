package me.whereareiam.anvil.workbench.remote.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRefresh;
import me.whereareiam.anvil.workbench.protocol.model.workspace.FinishWorkspace;
import me.whereareiam.anvil.workbench.protocol.model.workspace.RefreshWorkspace;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * A workspace prepared on a workbench on another machine, refreshed there before every generation of its process
 * and finished there.
 */
@RequiredArgsConstructor
final class RemotePreparedWorkspace implements PreparedWorkspace {
	private final @NotNull WorkbenchConnection connection;
	private final @NotNull String handle;
	private final @NotNull Path workspace;

	@Override
	public @NotNull Path workspace() {
		return workspace;
	}

	@Override
	public void refresh(@NotNull WorkspaceRefresh refresh) {
		connection.call(new RefreshWorkspace(handle, refresh));
	}

	@Override
	public void finish(boolean successful) {
		connection.call(new FinishWorkspace(handle, successful));
	}
}
