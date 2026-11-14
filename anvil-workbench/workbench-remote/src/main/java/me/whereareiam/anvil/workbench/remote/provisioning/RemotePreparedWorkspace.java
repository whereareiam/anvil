package me.whereareiam.anvil.workbench.remote.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.workbench.protocol.model.workspace.FinishWorkspace;
import me.whereareiam.anvil.workbench.protocol.model.workspace.StartWorkspace;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * A workspace prepared on a workbench on another machine, brought up to date there before every start of its process
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
	public void beforeStart(@NotNull List<WorkspaceAsset> assets) {
		connection.call(new StartWorkspace(handle, List.copyOf(assets)));
	}

	@Override
	public void finish(boolean successful) {
		connection.call(new FinishWorkspace(handle, successful));
	}
}
