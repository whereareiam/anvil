package me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Coordinates complete workspace snapshot publication and restoration.
 * Workspace provisioning owns snapshot identities, formats, and content validation.
 */
public interface WorkspaceSnapshotStore {
	/**
	 * Restores selected contents under exclusive snapshot access.
	 * The destination is already validated and exclusively owned by the workspace lifecycle.
	 * A missing snapshot leaves the destination unchanged.
	 *
	 * @param snapshot snapshot location selected by workspace provisioning
	 * @param destination validated destination to replace
	 * @param transfer validates and selects contents while snapshot access is held
	 * @return whether selected contents were successfully published
	 * @throws IOException when access, copying, publication, or rollback fails
	 */
	boolean restore(@NotNull Path snapshot, @NotNull Path destination, @NotNull WorkspaceSnapshotTransfer transfer) throws IOException;

	/**
	 * Publishes a complete replacement under exclusive snapshot access.
	 * Failed preparation preserves the previous snapshot; an absent source leaves it unchanged.
	 *
	 * @param snapshot snapshot location selected by workspace provisioning
	 * @param transfer validates existing storage and selects replacement contents
	 * @throws IOException when access, copying, publication, or rollback fails
	 */
	void save(@NotNull Path snapshot, @NotNull WorkspaceSnapshotTransfer transfer) throws IOException;
}
