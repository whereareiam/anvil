package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * A workspace the workbench prepared.
 */
@Value
public class PreparedWorkspaceHandle {
	/**
	 * Handle the engine finishes it by.
	 */
	@NotNull String handle;

	/**
	 * The workspace on the workbench.
	 */
	@NotNull Path workspace;
}
