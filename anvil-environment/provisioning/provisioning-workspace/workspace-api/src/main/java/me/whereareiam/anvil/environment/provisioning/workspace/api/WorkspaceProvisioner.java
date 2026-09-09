package me.whereareiam.anvil.environment.provisioning.workspace.api;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRequest;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Prepares process workspaces and maintains their containing scenario-run directories.
 * The caller owns lifecycle timing; this service owns confined filesystem operations and policies.
 */
public interface WorkspaceProvisioner {
	/**
	 * Resolves a new run directory and the process directories without accessing the filesystem.
	 * Fresh process directories belong to this run; persistent directories remain stable across runs
	 * of the same scenario. Distinct processes must not resolve to the same directory.
	 *
	 * @param root configured workspace root
	 * @param scenario structurally valid scenario with unique process names
	 * @return immutable absolute directory paths for one scenario run
	 * @throws ScenarioValidationException if a name cannot safely identify a directory or two process
	 * directories collide
	 */
	@NotNull WorkspaceLayout layout(@NotNull Path root, @NotNull AnvilScenario scenario);

	/**
	 * Validates a request, acquires directory ownership, restores snapshots, and installs assets.
	 * Preparation failure releases acquired resources and applies failure cleanup.
	 *
	 * @param request resolved process inputs and workspace declarations
	 * @return caller-owned workspace retained until the run is finalized
	 */
	@NotNull PreparedWorkspace prepare(@NotNull WorkspaceRequest request);

	/**
	 * Creates an empty scenario-run directory beneath the configured workspace root.
	 *
	 * @param root configured workspace root
	 * @param directory run directory strictly beneath the root
	 */
	void recreateRunDirectory(@NotNull Path root, @NotNull Path directory);

	/**
	 * Finalizes the containing run directory after every process workspace has finished.
	 * Successful runs are removed. Failed runs are retained only when requested; persistent process
	 * directories are siblings of the run directory and remain available for future runs.
	 *
	 * @param root configured workspace root
	 * @param directory completed run directory strictly beneath the root
	 * @param successful whether preparation, execution, and process finalization succeeded
	 * @param keepFailedWorkspaces whether failed run directories should remain for diagnostics
	 */
	void finishRunDirectory(@NotNull Path root, @NotNull Path directory, boolean successful, boolean keepFailedWorkspaces);

	/**
	 * Removes a completed scenario-run directory, preserving paths outside the configured root.
	 *
	 * @param root configured workspace root
	 * @param directory completed run directory strictly beneath the root
	 */
	void deleteRunDirectory(@NotNull Path root, @NotNull Path directory);
}
