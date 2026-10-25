package me.whereareiam.anvil.workbench.remote.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRequest;
import me.whereareiam.anvil.workbench.protocol.model.workspace.DeleteRunDirectory;
import me.whereareiam.anvil.workbench.protocol.model.workspace.FinishRunDirectory;
import me.whereareiam.anvil.workbench.protocol.model.workspace.LayoutWorkspaces;
import me.whereareiam.anvil.workbench.protocol.model.workspace.PrepareWorkspace;
import me.whereareiam.anvil.workbench.protocol.model.workspace.PreparedWorkspaceHandle;
import me.whereareiam.anvil.workbench.protocol.model.workspace.RecreateRunDirectory;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Lays out, prepares and retains process workspaces on a workbench on another machine.
 */
@RequiredArgsConstructor
public final class RemoteWorkspaces implements WorkspaceProvisioner {
	private final @NotNull WorkbenchConnection connection;

	@Override
	public @NotNull WorkspaceLayout layout(@NotNull Path root, @NotNull AnvilScenario scenario) {
		return connection.call(new LayoutWorkspaces(root, scenario));
	}

	/**
	 * Prepares a workspace; text assets are filled in on the engine, where their placeholders resolve.
	 */
	@Override
	public @NotNull PreparedWorkspace prepare(@NotNull WorkspaceRequest request) {
		WorkspacePlan plan = request.getPlan().toBuilder().clearAssets().assets(request.getPlan().getAssets().stream()
				.map(asset -> asset.getSource().isText()
						? asset.toBuilder().source(AssetSource.text(request.getText().apply(asset.getSource().getText()))).build()
						: asset)
				.toList()).build();
		PreparedWorkspaceHandle prepared = connection.call(new PrepareWorkspace(request.getRoot(), request.getDirectory(),
				request.getProcess(), plan, request.getProviderDefaults()));

		return new RemotePreparedWorkspace(connection, prepared.getHandle(), prepared.getWorkspace());
	}

	@Override
	public @NotNull ProcessFiles files(@NotNull Path workspace) {
		return new RemoteProcessFiles(connection, workspace);
	}

	@Override
	public void recreateRunDirectory(@NotNull Path root, @NotNull Path directory) {
		connection.call(new RecreateRunDirectory(root, directory));
	}

	@Override
	public void finishRunDirectory(@NotNull Path root, @NotNull Path directory, boolean successful, boolean keepFailedWorkspaces) {
		connection.call(new FinishRunDirectory(root, directory, successful, keepFailedWorkspaces));
	}

	@Override
	public void deleteRunDirectory(@NotNull Path root, @NotNull Path directory) {
		connection.call(new DeleteRunDirectory(root, directory));
	}
}
