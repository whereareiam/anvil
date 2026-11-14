package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Brings a prepared workspace up to date for a start of its process, before every start.
 */
@Value
public class StartWorkspace implements WorkspaceProvisioningRequest<Done> {
	/**
	 * Handle of the prepared workspace.
	 */
	@NotNull String handle;

	/**
	 * The workspace's assets with their current sources on the workbench.
	 */
	@NotNull List<WorkspaceAsset> assets;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
