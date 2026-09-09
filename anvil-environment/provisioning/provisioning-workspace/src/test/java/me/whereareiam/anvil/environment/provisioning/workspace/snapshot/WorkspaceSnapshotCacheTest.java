package me.whereareiam.anvil.environment.provisioning.workspace.snapshot;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.cache.api.model.CacheKey;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSnapshotCacheTest {
	@TempDir
	Path temporary;

	private WorkspaceSnapshotCache snapshots;
	private Path cacheDirectory;

	@BeforeEach
	void createStore() {
		cacheDirectory = temporary.resolve("cache");
		snapshots = new WorkspaceSnapshotCache(cacheDirectory, new TestWorkspaceSnapshotStore(new FileCache(cacheDirectory)));
	}

	@Test
	void restoresFileAndReplacesItsPreviousContents() throws Exception {
		Path source = Files.writeString(temporary.resolve("library.jar"), "first");
		Path destination = Files.writeString(temporary.resolve("restored.jar"), "outdated");
		snapshots.save("paper|1", "library", source);

		assertTrue(snapshots.restore("paper|1", "library", destination));
		assertEquals("first", Files.readString(destination));

		Files.writeString(source, "second");
		snapshots.save("paper|1", "library", source);
		assertTrue(snapshots.restore("paper|1", "library", destination));
		assertEquals("second", Files.readString(destination));
		assertNoTemporaryEntries();
	}
	@Test
	void restoresDirectoryContentsIncludingEmptyDirectoriesWithoutAnOverlay() throws Exception {
		Path source = Files.createDirectory(temporary.resolve("libraries"));
		Files.createDirectories(source.resolve("nested/empty"));
		Files.writeString(source.resolve("nested/library.jar"), "library");
		Path destination = Files.createDirectory(temporary.resolve("restored"));
		Files.writeString(destination.resolve("obsolete.jar"), "remove");
		snapshots.save("paper|1", "libraries", source);

		assertTrue(snapshots.restore("paper|1", "libraries", destination));
		assertEquals("library", Files.readString(destination.resolve("nested/library.jar")));
		assertTrue(Files.isDirectory(destination.resolve("nested/empty")));
		assertFalse(Files.exists(destination.resolve("obsolete.jar")));
		assertNoTemporaryEntries();
	}
	@Test
	void replacesFileSnapshotsWithDirectoriesAndDirectoriesWithFiles() throws Exception {
		Path file = Files.writeString(temporary.resolve("source-file"), "file");
		Path directory = Files.createDirectory(temporary.resolve("source-directory"));
		Files.writeString(directory.resolve("child"), "directory");
		Path destination = temporary.resolve("restored");

		snapshots.save("identity", "key", file);
		assertTrue(snapshots.restore("identity", "key", destination));
		assertEquals("file", Files.readString(destination));

		snapshots.save("identity", "key", directory);
		assertTrue(snapshots.restore("identity", "key", destination));
		assertEquals("directory", Files.readString(destination.resolve("child")));

		snapshots.save("identity", "key", file);
		assertTrue(snapshots.restore("identity", "key", destination));
		assertEquals("file", Files.readString(destination));
		assertNoTemporaryEntries();
	}
	@Test
	void missingSnapshotLeavesDestinationUntouchedAndMissingSourcePreservesSnapshot() throws Exception {
		Path absent = temporary.resolve("absent/destination");
		assertFalse(snapshots.restore("missing", "key", absent));
		assertFalse(Files.exists(absent.getParent()), "Missing snapshots must not allocate destination staging");

		Path destination = Files.writeString(temporary.resolve("destination"), "keep");
		assertFalse(snapshots.restore("missing", "key", destination));
		assertEquals("keep", Files.readString(destination));

		Path source = Files.writeString(temporary.resolve("source"), "saved");
		snapshots.save("identity", "key", source);
		Files.delete(source);
		snapshots.save("identity", "key", source);

		assertTrue(snapshots.restore("identity", "key", destination));
		assertEquals("saved", Files.readString(destination));
	}
	@Test
	void separatesIdentitiesAndKeys() throws Exception {
		Path source = temporary.resolve("source");
		snapshots.save("paper|1", "libraries", Files.writeString(source, "one"));
		snapshots.save("paper|2", "libraries", Files.writeString(source, "two"));
		snapshots.save("paper|1", "other", Files.writeString(source, "three"));
		Path destination = temporary.resolve("destination");

		assertTrue(snapshots.restore("paper|1", "libraries", destination));
		assertEquals("one", Files.readString(destination));
		assertTrue(snapshots.restore("paper|2", "libraries", destination));
		assertEquals("two", Files.readString(destination));
		assertTrue(snapshots.restore("paper|1", "other", destination));
		assertEquals("three", Files.readString(destination));
	}
	@Test
	void restoresTheExistingSnapshotLayout() throws Exception {
		Path entry = entry("paper|1\nassets=abc", "libraries:cache");
		Files.createDirectories(entry.resolve("payload/nested"));
		Files.writeString(entry.resolve("payload/nested/library.jar"), "existing");
		Files.writeString(entry.resolve("key"), "libraries:cache");
		Path destination = temporary.resolve("destination");

		assertTrue(snapshots.restore("paper|1\nassets=abc", "libraries:cache", destination));
		assertEquals("existing", Files.readString(destination.resolve("nested/library.jar")));
		assertEquals("libraries:cache", Files.readString(entry.resolve("key")));
	}
	@Test
	void failedStagingPreservesThePreviousSnapshotAndRemovesTemporaryData() throws Exception {
		Path source = Files.createDirectory(temporary.resolve("source"));
		Files.writeString(source.resolve("valid"), "previous");
		snapshots.save("identity", "key", source);
		Files.writeString(source.resolve("valid"), "replacement");
		Files.createSymbolicLink(source.resolve("invalid"), temporary.resolve("missing"));

		ProvisioningException failure = assertThrows(ProvisioningException.class,
				() -> snapshots.save("identity", "key", source));
		assertTrue(failure.getMessage().contains("symbolic link"));
		assertNoTemporaryEntries();
		Path destination = temporary.resolve("destination");
		assertTrue(snapshots.restore("identity", "key", destination));
		assertEquals("previous", Files.readString(destination.resolve("valid")));
		assertFalse(Files.exists(destination.resolve("invalid")));

		Files.delete(source.resolve("invalid"));
		snapshots.save("identity", "key", source);
		assertTrue(snapshots.restore("identity", "key", destination));
		assertEquals("replacement", Files.readString(destination.resolve("valid")));
	}
	@Test
	void rejectsSymbolicSourceAndPayloadWithoutReplacingDestination() throws Exception {
		Path source = Files.writeString(temporary.resolve("source"), "original");
		Path link = Files.createSymbolicLink(temporary.resolve("link"), source);
		assertThrows(ProvisioningException.class, () -> snapshots.save("identity", "key", link));
		assertNoTemporaryEntries();

		Path entry = entry("identity", "key");
		Files.createDirectories(entry);
		Files.createSymbolicLink(entry.resolve("payload"), source);
		Path destination = Files.writeString(temporary.resolve("destination"), "keep");
		assertThrows(ProvisioningException.class, () -> snapshots.restore("identity", "key", destination));
		assertEquals("keep", Files.readString(destination));
		assertNoTemporaryEntries();
	}

	@Test
	void reportsCacheAccessFailuresAsProvisioningFailuresWithoutChangingFiles() throws Exception {
		CacheException storageFailure = new CacheException("Shared cache is inaccessible");
		FileCache storage = new FileCache(cacheDirectory);
		Cache unavailable = new Cache() {
			@Override
			public @NotNull Path path(@NotNull CacheKey key) {
				return storage.path(key);
			}

			@Override
			public @NotNull CacheEntry open(@NotNull Path entry) {
				throw storageFailure;
			}
		};
		WorkspaceSnapshotCache snapshots = new WorkspaceSnapshotCache(cacheDirectory, new TestWorkspaceSnapshotStore(unavailable));
		Path source = Files.writeString(temporary.resolve("source"), "source contents");
		Path destination = Files.writeString(temporary.resolve("destination"), "previous contents");

		ProvisioningException save = assertThrows(ProvisioningException.class,
				() -> snapshots.save("identity", "libraries", source));
		assertSame(storageFailure, save.getCause().getCause());
		assertTrue(save.getMessage().contains("libraries"));
		ProvisioningException restore = assertThrows(ProvisioningException.class,
				() -> snapshots.restore("identity", "libraries", destination));
		assertSame(storageFailure, restore.getCause().getCause());
		assertTrue(restore.getMessage().contains("libraries"));
		assertEquals("source contents", Files.readString(source));
		assertEquals("previous contents", Files.readString(destination));
	}

	private Path entry(String identity, String key) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest((identity + "\n" + key).getBytes(StandardCharsets.UTF_8));

		return cacheDirectory.resolve("workspaces").resolve(HexFormat.of().formatHex(hash));
	}

	private void assertNoTemporaryEntries() throws Exception {
		try (var paths = Files.walk(temporary)) {
			List<Path> temporaryEntries = paths.filter(path -> {
				String name = path.getFileName().toString();
				return name.contains(".part-") || name.contains(".previous-");
			}).toList();
			assertEquals(List.of(), temporaryEntries);
		}
	}
}
