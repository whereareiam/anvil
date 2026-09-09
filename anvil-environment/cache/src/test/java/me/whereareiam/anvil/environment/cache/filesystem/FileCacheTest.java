package me.whereareiam.anvil.environment.cache.filesystem;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.cache.api.model.CacheKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCacheTest {
	@TempDir
	Path temporary;

	@Test
	void preservesLogicalKeyLocationsAndOptionalSuffixes() {
		Cache cache = new FileCache(temporary);
		CacheKey key = CacheKey.builder().namespace("workspaces").value("identity\nkey").build();
		assertEquals(temporary.resolve("workspaces/501e778e6a6fe9df6c210169b7a80b2ba09b57571b308d28f27ae7ab8de25066"),
				cache.path(key));
		assertEquals(temporary.resolve("metadata/b7fc3d9f77d867bb652fceda9ec916c906ee53d31861e31211bb2c828be9727c.json"),
				cache.path(CacheKey.builder().namespace("metadata").value("https://cache.example/metadata")
						.suffix(".json").build()));
	}

	@Test
	void rejectsKeysThatEscapeTheirCacheNamespace() {
		Cache cache = new FileCache(temporary);
		assertThrows(IllegalArgumentException.class, () -> cache.path(CacheKey.builder()
				.namespace("../outside").value("key").build()));
		assertThrows(IllegalArgumentException.class, () -> cache.path(CacheKey.builder()
				.namespace(temporary.toString()).value("key").build()));
		assertThrows(IllegalArgumentException.class, () -> cache.path(CacheKey.builder()
				.namespace("metadata").value("key").suffix("/../outside").build()));
	}

	@Test
	void openingAKeyDoesNotCreateItsPayload() {
		Cache cache = new FileCache(temporary);
		CacheKey key = CacheKey.builder().namespace("prepared").value("java").build();
		try (CacheEntry entry = cache.open(key)) {
			assertEquals(cache.path(key), entry.path());
			assertFalse(Files.exists(entry.path()));
		}
	}

	@Test
	void entryCloseDiscardsOutstandingWritesAndEndsTheirLifetime() throws Exception {
		Cache cache = new FileCache(temporary.resolve("cache"));
		Path target = Files.writeString(temporary.resolve("entry"), "previous");
		Path restored = Files.writeString(temporary.resolve("destination"), "untouched");
		try (CacheEntry entry = cache.open(target)) {
			var file = entry.stageFile();
			Files.writeString(file.path(), "unpublished");
			var directory = entry.stageReplacement(restored);
			Files.createDirectories(directory.path().resolve("nested"));
			Files.writeString(directory.path().resolve("nested/data"), "unpublished");

			entry.close();
			assertFalse(Files.exists(file.path()));
			assertFalse(Files.exists(directory.path()));
			assertEquals("previous", Files.readString(target));
			assertEquals("untouched", Files.readString(restored));
			assertThrows(IllegalStateException.class, entry::stageFile);
			assertThrows(IllegalStateException.class, file::commit);
			assertThrows(IllegalStateException.class, directory::commit);
		}
	}

	@Test
	void cleanupFailuresAreCombinedAndDoNotRetainTheEntryLock() throws Exception {
		Cache cache = new FileCache(temporary.resolve("cache"));
		Path target = temporary.resolve("entry");
		URI archive = URI.create("jar:" + temporary.resolve("staging.zip").toUri());
		try (CacheEntry entry = cache.open(target)) {
			try (FileSystem filesystem = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
				var first = entry.stageReplacement(filesystem.getPath("/first"));
				var second = entry.stageReplacement(filesystem.getPath("/second"));
				Files.writeString(first.path(), "first");
				Files.writeString(second.path(), "second");
			}

			CacheException failure = assertThrows(CacheException.class, entry::close);
			assertInstanceOf(ClosedFileSystemException.class, failure.getCause());
			assertEquals(1, failure.getSuppressed().length);
			assertInstanceOf(ClosedFileSystemException.class, failure.getSuppressed()[0]);
			try (CacheEntry reopened = new FileCache(temporary.resolve("cache")).open(target)) {
				assertEquals(target, reopened.path());
			}
		}
	}

	@Test
	void independentInstancesWaitUntilOutstandingWritesHaveBeenCleaned() throws Exception {
		Path root = temporary.resolve("cache");
		Path target = Files.writeString(temporary.resolve("entry"), "previous");
		CacheEntry entry = new FileCache(root).open(target);
		Path staged = entry.stageReplacement().path();
		Files.createDirectories(staged);
		Files.writeString(staged.resolve("incomplete"), "discard");
		CountDownLatch attempted = new CountDownLatch(1);
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor(); entry) {
			var waiting = tasks.submit(() -> {
				attempted.countDown();
				try (CacheEntry acquired = new FileCache(root).open(target)) {
					assertFalse(Files.exists(staged), "Outstanding writes must be cleaned before access is released");
					return Files.readString(acquired.path());
				}
			});
			assertTrue(attempted.await(5, TimeUnit.SECONDS));
			assertThrows(TimeoutException.class, () -> waiting.get(150, TimeUnit.MILLISECONDS));
			entry.close();
			assertEquals("previous", waiting.get(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void unrelatedEntriesCanProgressWhileAnotherEntryIsHeld() throws Exception {
		Cache cache = new FileCache(temporary.resolve("cache"));
		Path first = temporary.resolve("first");
		Path second = temporary.resolve("second");
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor();
		     CacheEntry ignored = cache.open(first)) {
			var result = tasks.submit(() -> {
				try (CacheEntry entry = cache.open(second)) {
					var write = entry.stageFile();
					Files.writeString(write.path(), "independent");
					write.commit();
					return entry.path();
				}
			});
			assertEquals(second, result.get(5, TimeUnit.SECONDS));
			assertEquals("independent", Files.readString(second));
		}
	}

	@Test
	void closingFromAnotherThreadDoesNotLoseTheOwnersAccess() throws Exception {
		Cache cache = new FileCache(temporary.resolve("cache"));
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor();
		     CacheEntry entry = cache.open(temporary.resolve("entry"))) {
			var failure = tasks.submit(() -> assertThrows(IllegalStateException.class, entry::close));
			failure.get(5, TimeUnit.SECONDS);
			try (var write = entry.stageFile()) {
				Files.writeString(write.path(), "owner still active");
				write.commit();
			}
		}
	}

	@Test
	void aChildJvmHoldsExclusiveAccessUntilPublicationFinishes() throws Exception {
		Path root = temporary.resolve("cache");
		Path target = temporary.resolve("entry");
		try (var tasks = Executors.newVirtualThreadPerTaskExecutor();
		     Child child = Child.start(root, target)) {
			assertEquals("READY", tasks.submit(child.output::readLine).get(5, TimeUnit.SECONDS));
			assertEquals("incomplete", Files.readString(target));
			CountDownLatch attempted = new CountDownLatch(1);
			var read = tasks.submit(() -> {
				attempted.countDown();
				try (CacheEntry entry = new FileCache(root).open(target)) {
					return Files.readString(entry.path());
				}
			});
			assertTrue(attempted.await(5, TimeUnit.SECONDS));
			assertThrows(TimeoutException.class, () -> read.get(150, TimeUnit.MILLISECONDS));
			child.release();
			assertEquals("complete", read.get(5, TimeUnit.SECONDS));
			assertTrue(child.process.waitFor(5, TimeUnit.SECONDS));
			assertEquals(0, child.process.exitValue());
		}
	}

	@RequiredArgsConstructor
	private static final class Child implements AutoCloseable {
		private final Process process;
		private final BufferedReader output;
		private final BufferedWriter input;

		private static Child start(Path root, Path entry) throws Exception {
			String classpath = Stream.of(CacheEntryLockChild.class, FileCache.class, Cache.class)
					.map(type -> Path.of(URI.create(type.getProtectionDomain().getCodeSource().getLocation().toString())).toString())
					.distinct().collect(Collectors.joining(File.pathSeparator));
			String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
			Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
					"-cp", classpath, CacheEntryLockChild.class.getName(), root.toString(), entry.toString())
					.redirectErrorStream(true).start();

			return new Child(process,
					new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)),
					new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)));
		}

		private void release() throws Exception {
			input.write("RELEASE\n");
			input.flush();
		}

		@Override
		public void close() throws Exception {
			if (process.isAlive()) {
				process.destroyForcibly();
				assertTrue(process.waitFor(5, TimeUnit.SECONDS));
			}
			try (input; output) {
				// The child process and its terminal streams belong to this test.
			}
		}
	}
}
