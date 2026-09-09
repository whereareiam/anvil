package me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Selects validated snapshot contents while their storage entry is exclusively held.
 */

public interface WorkspaceSnapshotTransfer {
	/**
	 * Checks the existing snapshot and selects contents before staging is allocated.
	 * @param snapshot locked current snapshot location, possibly absent
	 * @return content writer, or null when no snapshot or source is available
	 * @throws IOException when snapshot or source validation fails
	 */
	@Nullable WorkspaceSnapshotContent prepare(@NotNull Path snapshot) throws IOException;
}
