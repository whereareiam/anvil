package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRefresh;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Brings a prepared workspace up to date before a generation of its process.
 */
@Value
public class RefreshWorkspace implements WorkspaceProvisioningRequest<Done> {
	/**
	 * Handle of the prepared workspace.
	 */
	@NotNull String handle;

	/**
	 * What the workspace is brought up to date with, its asset sources being the workbench's copies.
	 */
	@NotNull WorkspaceRefresh refresh;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
