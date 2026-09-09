package me.whereareiam.anvil.launcher.assembly.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot.WorkspaceSnapshotStore;
import me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot.WorkspaceSnapshotTransfer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Publishes validated workspace content while its source snapshot remains exclusively leased.
 */
@RequiredArgsConstructor
final class CacheWorkspaceSnapshotStore implements WorkspaceSnapshotStore {
	private final @NotNull Cache cache;

	@Override
	public boolean restore(
			@NotNull Path snapshot,
			@NotNull Path destination,
			@NotNull WorkspaceSnapshotTransfer transfer
	) throws IOException {
		return publish(snapshot, destination, transfer);
	}

	@Override
	public void save(@NotNull Path snapshot, @NotNull WorkspaceSnapshotTransfer transfer) throws IOException {
		publish(snapshot, snapshot, transfer);
	}

	private boolean publish(
			@NotNull Path snapshot,
			@NotNull Path destination,
			@NotNull WorkspaceSnapshotTransfer transfer
	) throws IOException {
		try (var entry = cache.open(snapshot)) {
			var content = transfer.prepare(entry.path());
			if (content == null) return false;

			try (var write = entry.stageReplacement(destination)) {
				content.write(write.path());
				write.commit();

				return true;
			}
		} catch (CacheException failure) {
			throw new IOException("Cannot access workspace snapshot " + snapshot, failure);
		}
	}
}
