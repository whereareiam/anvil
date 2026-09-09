package me.whereareiam.anvil.environment.cache.filesystem;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.CacheWrite;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns staged writes and the exclusive access protecting their publication.
 */
@RequiredArgsConstructor
final class LockedFileCacheEntry implements CacheEntry {
	private final @NotNull Path path;
	private final @NotNull FileChannel channel;
	private final @NotNull FileLock fileLock;
	private final @NotNull ReentrantLock threadLock;
	private final @NotNull Thread owner = Thread.currentThread();
	private final List<StagedWrite> writes = new ArrayList<>();
	private boolean closed;

	@Override
	public @NotNull Path path() {
		return path;
	}

	@Override
	public @NotNull CacheWrite stageFile() throws IOException {
		ensureOpen();
		Path target = prepareParent(path);
		Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".part-", "");
		StagedWrite write = StagedWrite.file(temporary, target);
		writes.add(write);

		return write;
	}

	@Override
	public @NotNull CacheWrite stageReplacement() throws IOException {
		return stageReplacement(path);
	}

	@Override
	public @NotNull CacheWrite stageReplacement(@NotNull Path destination) throws IOException {
		ensureOpen();
		Path target = prepareParent(destination);
		Path temporary = target.resolveSibling(target.getFileName() + ".part-" + UUID.randomUUID());
		StagedWrite write = StagedWrite.replacement(temporary, target);
		writes.add(write);

		return write;
	}

	@Override
	public void close() {
		if (closed) return;
		ensureOwner();
		closed = true;

		CacheException failure = null;
		try {
			for (StagedWrite write : writes.reversed())
				failure = closeOwned(write, failure);
			writes.clear();
			failure = closeOwned(fileLock, failure);
			failure = closeOwned(channel, failure);
		} finally {
			threadLock.unlock();
		}

		if (failure != null) throw failure;
	}

	private Path prepareParent(Path destination) throws IOException {
		Path target = destination.toAbsolutePath().normalize();
		if (target.getParent() == null)
			throw new IllegalArgumentException("Cache write target must not be a filesystem root: " + target);
		Files.createDirectories(target.getParent());

		return target;
	}

	private void ensureOpen() {
		ensureOwner();
		if (closed) throw new IllegalStateException("Cache entry is closed: " + path);
	}

	private void ensureOwner() {
		if (Thread.currentThread() != owner)
			throw new IllegalStateException("Cache entry must be used on the thread that opened it: " + path);
	}

	private @Nullable CacheException closeOwned(AutoCloseable resource, @Nullable CacheException failure) {
		try {
			resource.close();
		} catch (Exception | Error cleanup) {
			if (failure == null) return new CacheException("Could not close cache entry " + path, cleanup);
			failure.addSuppressed(cleanup);
		}

		return failure;
	}
}
