package me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes one complete workspace snapshot or restored payload into unpublished storage.
 */

public interface WorkspaceSnapshotContent {
	/**
	 * Prepares complete contents before the storage transaction publishes them.
	 * @param staging reserved, initially absent destination for a file or directory
	 * @throws IOException when contents cannot be copied or written
	 */
	void write(@NotNull Path staging) throws IOException;
}
