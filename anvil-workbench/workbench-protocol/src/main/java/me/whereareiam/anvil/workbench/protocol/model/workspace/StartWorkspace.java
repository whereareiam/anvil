package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Brings a prepared workspace up to date for a start of its process, before every start.
 */
@Value
public class StartWorkspace implements WorkspaceProvisioningRequest<Done> {
	/**
	 * Handle of the prepared workspace.
	 */
	@NotNull String handle;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
