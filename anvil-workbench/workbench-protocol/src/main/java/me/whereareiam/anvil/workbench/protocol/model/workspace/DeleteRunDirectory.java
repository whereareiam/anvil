package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Deletes a scenario run's directory.
 */
@Value
public class DeleteRunDirectory implements WorkspaceProvisioningRequest<Done> {
	/**
	 * Work root on the workbench.
	 */
	@NotNull Path root;

	/**
	 * The run's directory.
	 */
	@NotNull Path directory;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
