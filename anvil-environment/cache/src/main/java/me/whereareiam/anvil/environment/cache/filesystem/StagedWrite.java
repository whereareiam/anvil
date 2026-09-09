package me.whereareiam.anvil.environment.cache.filesystem;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import me.whereareiam.anvil.environment.cache.api.CacheWrite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/**
 * Owns one staged path and the backup needed when replacing an arbitrary file or directory.
 *
 * <p>Callers hold the relevant cache-entry lease through commit and close. File publication uses
 * direct replacement; path publication moves the previous contents aside and attempts rollback
 * after failure. A failed rollback retains its backup. Process crashes are not recovered here.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class StagedWrite implements CacheWrite {
	@Getter
	@Accessors(fluent = true)
	private final @NotNull Path path;
	private final @NotNull Path target;
	private final @NotNull Publication publication;
	private final @NotNull Thread owner = Thread.currentThread();

	private @Nullable Path previous;
	private boolean committed;
	private boolean closed;

	static @NotNull StagedWrite file(@NotNull Path path, @NotNull Path target) {
		return new StagedWrite(path, target, Publication.FILE);
	}

	static @NotNull StagedWrite replacement(@NotNull Path path, @NotNull Path target) {
		return new StagedWrite(path, target, Publication.PATH);
	}

	@Override
	public void commit() throws IOException {
		ensureOwner();
		if (closed) throw new IllegalStateException("Staged cache write is closed");
		if (committed) return;

		if (publication == Publication.FILE) {
			replaceFile();
			committed = true;
			return;
		}

		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
			Path backup = target.resolveSibling(target.getFileName() + ".previous-" + UUID.randomUUID());
			move(target, backup);
			previous = backup;
		}

		try {
			move(path, target);
			committed = true;
		} catch (IOException | RuntimeException | Error failure) {
			rollback(failure);
			throw failure;
		}
	}

	@Override
	public void close() throws IOException {
		if (closed) return;
		ensureOwner();
		closed = true;

		Throwable failure = cleanup(path, null);
		if (committed && previous != null) failure = cleanup(previous, failure);
		if (failure instanceof IOException checked) throw checked;
		if (failure instanceof RuntimeException unchecked) throw unchecked;
		if (failure instanceof Error fatal) throw fatal;
	}

	private void ensureOwner() {
		if (Thread.currentThread() != owner)
			throw new IllegalStateException("Cache write must be used on the thread that opened its entry");
	}

	private void replaceFile() throws IOException {
		try {
			Files.move(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void move(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, destination);
		}
	}

	private void rollback(Throwable failure) {
		if (previous == null) return;

		try {
			move(previous, target);
			previous = null;
		} catch (IOException | RuntimeException | Error rollback) {
			failure.addSuppressed(rollback);
		}
	}

	private @Nullable Throwable cleanup(Path target, @Nullable Throwable failure) {
		try {
			delete(target);
		} catch (IOException | RuntimeException | Error cleanup) {
			if (failure == null) return cleanup;
			failure.addSuppressed(cleanup);
		}

		return failure;
	}

	private void delete(Path target) throws IOException {
		if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) return;

		Files.walkFileTree(target, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				Files.delete(file);

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
				if (failure != null) throw failure;
				Files.delete(directory);

				return FileVisitResult.CONTINUE;
			}
		});
	}

	private enum Publication {
		FILE,
		PATH
	}
}
