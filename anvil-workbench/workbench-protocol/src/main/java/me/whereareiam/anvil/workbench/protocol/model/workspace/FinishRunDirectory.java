package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Finishes a scenario run's directory, keeping a failed one when asked.
 */
@Value
public class FinishRunDirectory implements WorkspaceProvisioningRequest<Done> {
	/**
	 * Work root on the workbench.
	 */
	@NotNull Path root;

	/**
	 * The run's directory.
	 */
	@NotNull Path directory;

	/**
	 * Whether the run succeeded.
	 */
	boolean successful;

	/**
	 * Whether a failed run's directory stays.
	 */
	boolean keepFailed;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
