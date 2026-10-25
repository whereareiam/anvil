package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Finishes a prepared workspace.
 */
@Value
public class FinishWorkspace implements WorkspaceProvisioningRequest<Done> {
	/**
	 * Handle of the prepared workspace.
	 */
	@NotNull String handle;

	/**
	 * Whether its process ended successfully.
	 */
	boolean successful;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
