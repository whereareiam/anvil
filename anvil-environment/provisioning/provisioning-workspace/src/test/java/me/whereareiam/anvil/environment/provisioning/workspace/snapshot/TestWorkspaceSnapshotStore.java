package me.whereareiam.anvil.environment.provisioning.workspace.snapshot;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.CacheWrite;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot.WorkspaceSnapshotContent;
import me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot.WorkspaceSnapshotStore;
import me.whereareiam.anvil.environment.provisioning.workspace.api.snapshot.WorkspaceSnapshotTransfer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

@RequiredArgsConstructor
public final class TestWorkspaceSnapshotStore implements WorkspaceSnapshotStore {
	private final Cache cache;

	@Override
	public boolean restore(@NotNull Path snapshot, @NotNull Path destination, @NotNull WorkspaceSnapshotTransfer transfer) throws IOException {
		return transfer(snapshot, destination, transfer);
	}

	@Override
	public void save(@NotNull Path snapshot, @NotNull WorkspaceSnapshotTransfer transfer) throws IOException {
		transfer(snapshot, snapshot, transfer);
	}

	private boolean transfer(Path snapshot, Path destination, WorkspaceSnapshotTransfer transfer) throws IOException {
		try (CacheEntry entry = cache.open(snapshot)) {
			WorkspaceSnapshotContent content = transfer.prepare(entry.path());
			if (content == null) return false;
			try (CacheWrite write = entry.stageReplacement(destination)) {
				content.write(write.path());
				write.commit();
			}
			return true;
		} catch (CacheException failure) {
			throw new IOException("Could not access snapshot storage", failure);
		}
	}
}
