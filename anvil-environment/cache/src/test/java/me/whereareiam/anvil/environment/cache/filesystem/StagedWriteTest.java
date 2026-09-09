package me.whereareiam.anvil.environment.cache.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagedWriteTest {
	@TempDir
	Path temporary;

	@Test
	void publishesACompletedFileAndRemovesItsStagingPath() throws Exception {
		FileCache storage = storage();
		Path target = Files.writeString(temporary.resolve("artifact.jar"), "previous");
		Path staged;
		try (var entry = storage.open(target);
		     var write = entry.stageFile()) {
			staged = write.path();
			assertTrue(Files.isRegularFile(staged));
			assertEquals(0, Files.size(staged));
			Files.writeString(staged, "replacement");
			assertEquals("previous", Files.readString(target));
			write.commit();
			assertEquals("replacement", Files.readString(target));
		}

		assertFalse(Files.exists(staged));
		assertEquals("replacement", Files.readString(target));
		assertNoTemporaryEntries();
	}

	@Test
	void replacesANonemptyDirectoryWithAFile() throws Exception {
		FileCache storage = storage();
		Path target = Files.createDirectory(temporary.resolve("payload"));
		Files.writeString(target.resolve("obsolete"), "previous");
		try (var entry = storage.open(target);
		     var write = entry.stageReplacement()) {
			assertFalse(Files.exists(write.path()));
			Files.writeString(write.path(), "replacement");
			write.commit();
		}

		assertEquals("replacement", Files.readString(target));
		assertNoTemporaryEntries();
	}

	@Test
	void replacesAFileWithACompleteDirectory() throws Exception {
		FileCache storage = storage();
		Path target = Files.writeString(temporary.resolve("payload"), "previous");
		try (var entry = storage.open(target);
		     var write = entry.stageReplacement()) {
			Files.createDirectories(write.path().resolve("nested"));
			Files.writeString(write.path().resolve("nested/value"), "replacement");
			write.commit();
		}

		assertEquals("replacement", Files.readString(target.resolve("nested/value")));
		assertNoTemporaryEntries();
	}

	@Test
	void abortingAFileWritePreservesItsOriginalFailureAndTarget() throws Exception {
		FileCache storage = storage();
		Path target = Files.writeString(temporary.resolve("artifact.jar"), "previous");
		IOException expected = new IOException("Download failed");

		IOException failure = assertThrows(IOException.class, () -> {
			try (var entry = storage.open(target);
			     var write = entry.stageFile()) {
				Files.writeString(write.path(), "incomplete");
				throw expected;
			}
		});

		assertSame(expected, failure);
		assertEquals("previous", Files.readString(target));
		assertNoTemporaryEntries();
	}

	@Test
	void abortingADirectoryWriteRemovesStagingWithoutFollowingSymbolicLinks() throws Exception {
		FileCache storage = storage();
		Path target = Files.writeString(temporary.resolve("payload"), "previous");
		Path outside = Files.createDirectory(temporary.resolve("outside"));
		Files.writeString(outside.resolve("keep"), "untouched");
		IllegalArgumentException expected = new IllegalArgumentException("Invalid source tree");

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> {
			try (var entry = storage.open(target);
			     var write = entry.stageReplacement()) {
				Files.createDirectories(write.path().resolve("nested"));
				Files.writeString(write.path().resolve("nested/incomplete"), "discard");
				Files.createSymbolicLink(write.path().resolve("link"), outside);
				throw expected;
			}
		});

		assertSame(expected, failure);
		assertEquals("previous", Files.readString(target));
		assertEquals("untouched", Files.readString(outside.resolve("keep")));
		assertNoTemporaryEntries();
	}

	@Test
	void publicationFailureRestoresThePreviousDirectory() throws Exception {
		FileCache storage = storage();
		Path target = Files.createDirectory(temporary.resolve("payload"));
		Files.writeString(target.resolve("keep"), "previous");

		try (var entry = storage.open(target);
		     var write = entry.stageReplacement()) {
			// An absent replacement fails publication after the old target has moved aside.
			assertThrows(IOException.class, write::commit);
			assertEquals("previous", Files.readString(target.resolve("keep")));
		}

		assertEquals("previous", Files.readString(target.resolve("keep")));
		assertNoTemporaryEntries();
	}

	@Test
	void filePublicationFailureLeavesThePreviousFileIntact() throws Exception {
		FileCache storage = storage();
		Path target = Files.writeString(temporary.resolve("artifact.jar"), "previous");
		try (var entry = storage.open(target);
		     var write = entry.stageFile()) {
			Files.delete(write.path());
			assertThrows(IOException.class, write::commit);
		}

		assertEquals("previous", Files.readString(target));
		assertNoTemporaryEntries();
	}

	private FileCache storage() {
		return new FileCache(temporary.resolve("cache"));
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
