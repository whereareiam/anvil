package me.whereareiam.anvil.environment.provisioning.workspace;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRequest;
import me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot.WorkspaceSnapshotStore;
import me.whereareiam.anvil.environment.provisioning.workspace.directory.WorkspaceFiles;
import me.whereareiam.anvil.environment.provisioning.workspace.directory.WorkspaceLayoutResolver;
import me.whereareiam.anvil.environment.provisioning.workspace.preparation.WorkspaceSession;
import me.whereareiam.anvil.environment.provisioning.workspace.snapshot.WorkspaceSnapshotCache;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Prepares confined process directories using shared cache storage for compatible snapshots.
 */
public final class DefaultWorkspaceProvisioner implements WorkspaceProvisioner {
	private final @NotNull WorkspaceSnapshotCache snapshots;

	private final @NotNull WorkspaceFiles files = new WorkspaceFiles();
	private final @NotNull WorkspaceLayoutResolver layouts = new WorkspaceLayoutResolver();

	public DefaultWorkspaceProvisioner(@NotNull Path root, @NotNull WorkspaceSnapshotStore storage) {
		snapshots = new WorkspaceSnapshotCache(root, storage);
	}

	@Override
	public @NotNull WorkspaceLayout layout(@NotNull Path root, @NotNull AnvilScenario scenario) {
		return layouts.resolve(root, scenario);
	}

	@Override
	public @NotNull PreparedWorkspace prepare(@NotNull WorkspaceRequest request) {
		return WorkspaceSession.prepare(request.getRoot(), request.getDirectory(), request.getPlan(),
				request.getProviderDefaults(), request.getProcess(), snapshots, files);
	}

	@Override
	public void recreateRunDirectory(@NotNull Path root, @NotNull Path directory) {
		files.recreate(root, directory);
	}

	@Override
	public void finishRunDirectory(@NotNull Path root, @NotNull Path directory, boolean successful, boolean keepFailedWorkspaces) {
		if (!successful && keepFailedWorkspaces) return;

		deleteRunDirectory(root, directory);
	}

	@Override
	public void deleteRunDirectory(@NotNull Path root, @NotNull Path directory) {
		files.deleteAbsolute(files.resolveDirectory(root, directory));
	}
}
