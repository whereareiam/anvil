package me.whereareiam.anvil.environment.provisioning.workspace.snapshot;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.cache.api.model.CacheKey;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorkspaceSnapshotConcurrencyTest {
	private static final String IDENTITY = "paper|1.21.11|132";
	private static final String KEY = "libraries";
	private static final int DEADLINE_SECONDS = 10;

	@TempDir
	Path directory;

	@Test
	void serializesIndependentWritersAndRestoresOnlyTheCompletedReplacement() throws Exception {
		Path cacheDirectory = directory.resolve("cache");
		Path entry = entry(cacheDirectory, KEY);
		Path firstSource = source("first", "first-generation");
		Path secondSource = source("second", "second-generation");
		Path destination = directory.resolve("restored");
		LeaseProbe first = new LeaseProbe(entry, true);
		LeaseProbe second = new LeaseProbe(entry, true);
		LeaseProbe reader = new LeaseProbe(entry, false);

		try (var tasks = new Tasks()) {
			var firstStore = store(cacheDirectory, first);
			var secondStore = store(cacheDirectory, second);
			var readerStore = store(cacheDirectory, reader);
			var firstWrite = tasks.run(() -> firstStore.save(IDENTITY, KEY, firstSource));
			try {
				assertEquals(entry, reached(first.acquired, firstWrite));
				var secondWrite = tasks.run(() -> secondStore.save(IDENTITY, KEY, secondSource));
				assertEquals(entry, reached(second.attempted, secondWrite));
				assertFalse(second.acquired.isDone(), "The first writer still owns the entry lease");

				first.release();
				completed(firstWrite);
				assertEquals(entry, reached(second.acquired, secondWrite));
				assertEquals(contents(firstSource), contents(entry.resolve("payload")));

				var restore = tasks.submit(() -> readerStore.restore(IDENTITY, KEY, destination));
				assertEquals(entry, reached(reader.attempted, restore));
				assertFalse(reader.acquired.isDone(), "The replacement writer still owns the entry lease");
				assertFalse(Files.exists(destination), "A reader must not publish an older snapshot while a writer owns the entry");

				second.release();
				completed(secondWrite);
				assertTrue(completed(restore));
				assertEquals(contents(secondSource), contents(destination));
				assertEquals(KEY, Files.readString(entry.resolve("key")));
			} finally {
				first.release();
				second.release();
			}
		}
	}

	@Test
	void differentKeysProgressWhileAnotherEntryIsHeldInTheSameStore() throws Exception {
		Path cacheDirectory = directory.resolve("cache");
		Path firstSource = source("first", "first-generation");
		Path secondSource = source("second", "independent-generation");
		LeaseProbe first = new LeaseProbe(entry(cacheDirectory, KEY), true);

		try (var tasks = new Tasks()) {
			var store = store(cacheDirectory, first);
			var firstWrite = tasks.run(() -> store.save(IDENTITY, KEY, firstSource));
			try {
				assertEquals(first.entry, reached(first.acquired, firstWrite));
				completed(tasks.run(() -> store.save(IDENTITY, "other-libraries", secondSource)));
				Path destination = directory.resolve("independent-restore");
				assertTrue(completed(tasks.submit(() -> store.restore(IDENTITY, "other-libraries", destination))));
				assertEquals(contents(secondSource), contents(destination));
				assertFalse(firstWrite.isDone(), "The other entry completed while the first lease remained gated");
			} finally {
				first.release();
			}
			completed(firstWrite);
		}
	}

	@Test
	void failedWriterReleasesTheEntryForAnIndependentStore() throws Exception {
		Path cacheDirectory = directory.resolve("cache");
		Path source = source("source", "recovered-generation");
		Path entry = entry(cacheDirectory, KEY);
		LeaseProbe failing = new LeaseProbe(entry, true);
		LeaseProbe succeeding = new LeaseProbe(entry, false);

		try (var tasks = new Tasks()) {
			var firstStore = store(cacheDirectory, failing);
			var secondStore = store(cacheDirectory, succeeding);
			var failedWrite = tasks.run(() -> firstStore.save(IDENTITY, KEY, source));
			try {
				assertEquals(entry, reached(failing.acquired, failedWrite));
				Files.writeString(cacheDirectory.resolve("workspaces"), "conflicting regular file");
			} finally {
				failing.release();
			}
			ExecutionException failure = assertThrows(ExecutionException.class, () -> completed(failedWrite));
			assertInstanceOf(ProvisioningException.class, failure.getCause());
			Files.delete(cacheDirectory.resolve("workspaces"));

			var retry = tasks.run(() -> secondStore.save(IDENTITY, KEY, source));
			assertEquals(entry, reached(succeeding.acquired, retry));
			completed(retry);
			Path destination = directory.resolve("restored-after-failure");
			assertTrue(completed(tasks.submit(() -> secondStore.restore(IDENTITY, KEY, destination))));
			assertEquals(contents(source), contents(destination));
		}
	}

	@Test
	void childJvmLeaseHidesAnIncompletePublicationFromRestore() throws Exception {
		Path cacheDirectory = directory.resolve("cache");
		Path entry = entry(cacheDirectory, KEY);
		Path expected = source("expected", "child-generation");
		Path destination = directory.resolve("restored-from-child");
		LeaseProbe reader = new LeaseProbe(entry, false);

		try (var tasks = new Tasks();
		     var child = ChildPublisher.start(cacheDirectory, entry, KEY, "child-generation")) {
			assertEquals("READY", completed(tasks.submit(child.output::readLine)),
					"The child must hold the real OS lease before the reader starts");
			assertEquals("incomplete", Files.readString(entry.resolve("payload/version.txt")));
			assertFalse(Files.exists(entry.resolve("payload/nested/data.txt")));

			var store = store(cacheDirectory, reader);
			var restore = tasks.submit(() -> store.restore(IDENTITY, KEY, destination));
			assertEquals(entry, reached(reader.attempted, restore));
			assertFalse(reader.acquired.isDone(), "A different JVM currently owns this cache entry");
			assertFalse(Files.exists(destination), "The incomplete publication must not reach the reader");

			child.publish();
			assertEquals("DONE", completed(tasks.submit(child.output::readLine)));
			assertTrue(child.process.waitFor(DEADLINE_SECONDS, TimeUnit.SECONDS), "Child publisher did not exit");
			assertEquals(0, child.process.exitValue());
			assertTrue(completed(restore));
			assertEquals(contents(expected), contents(destination));
		}
	}

	private WorkspaceSnapshotCache store(Path root, LeaseProbe probe) {
		return new WorkspaceSnapshotCache(root, new TestWorkspaceSnapshotStore(new ProbedCache(new FileCache(root), probe)));
	}

	private Path source(String name, String version) throws Exception {
		Path source = directory.resolve(name);
		Files.createDirectories(source.resolve("nested"));
		Files.writeString(source.resolve("version.txt"), version);
		Files.writeString(source.resolve("nested/data.txt"), version.repeat(1_000));

		return source;
	}

	private Path entry(Path cache, String key) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest((IDENTITY + "\n" + key).getBytes(StandardCharsets.UTF_8));

		return cache.resolve("workspaces").resolve(HexFormat.of().formatHex(digest));
	}

	private Map<Path, String> contents(Path directory) throws Exception {
		Map<Path, String> contents = new LinkedHashMap<>();
		try (var paths = Files.walk(directory)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
				contents.put(directory.relativize(path), Files.readString(path));
		}

		return contents;
	}

	private Path reached(CompletableFuture<Path> signal, CompletableFuture<?> operation) throws Exception {
		completed(CompletableFuture.anyOf(signal, operation));
		if (!signal.isDone()) {
			completed(operation);
			fail("Snapshot operation completed without reaching the shared entry lease");
		}

		return completed(signal);
	}

	private static <T> T completed(CompletableFuture<T> future) throws Exception {
		return future.get(DEADLINE_SECONDS, TimeUnit.SECONDS);
	}

	@RequiredArgsConstructor
	private static final class LeaseProbe {
		private final Path entry;
		private final boolean paused;
		private final CompletableFuture<Path> attempted = new CompletableFuture<>();
		private final CompletableFuture<Path> acquired = new CompletableFuture<>();
		private final CountDownLatch continuation = new CountDownLatch(1);

		private void awaitContinuation(Path acquiredEntry) {
			if (!paused || !entry.equals(acquiredEntry)) return;
			try {
				if (!continuation.await(DEADLINE_SECONDS, TimeUnit.SECONDS))
					throw new IllegalStateException("Timed out waiting to continue a gated snapshot operation");
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted during a gated snapshot operation", failure);
			}
		}

		private void release() {
			continuation.countDown();
		}
	}

	@RequiredArgsConstructor
	private static final class ProbedCache implements Cache {
		private final Cache cache;
		private final LeaseProbe probe;

		@Override
		public @NotNull Path path(@NotNull CacheKey key) {
			return cache.path(key);
		}

		@Override
		public @NotNull CacheEntry open(@NotNull Path entry) {
			probe.attempted.complete(entry);
			CacheEntry lease = cache.open(entry);
			probe.acquired.complete(entry);
			try {
				probe.awaitContinuation(entry);
				return lease;
			} catch (RuntimeException | Error failure) {
				try {
					lease.close();
				} catch (RuntimeException | Error cleanup) {
					failure.addSuppressed(cleanup);
				}
				throw failure;
			}
		}
	}

	private static final class Tasks implements AutoCloseable {
		private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

		private CompletableFuture<Void> run(Runnable action) {
			return submit(() -> {
				action.run();
				return null;
			});
		}

		private <T> CompletableFuture<T> submit(Callable<T> action) {
			CompletableFuture<T> result = new CompletableFuture<>();
			executor.submit(() -> {
				try {
					result.complete(action.call());
				} catch (Throwable failure) {
					result.completeExceptionally(failure);
				}
			});

			return result;
		}

		@Override
		public void close() throws InterruptedException {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(DEADLINE_SECONDS, TimeUnit.SECONDS), "Snapshot tasks did not terminate");
		}
	}

	@RequiredArgsConstructor
	private static final class ChildPublisher implements AutoCloseable {
		private final Process process;
		private final BufferedReader output;
		private final BufferedWriter input;

		private static ChildPublisher start(Path cache, Path entry, String key, String version) throws Exception {
			String classpath = Stream.of(WorkspaceSnapshotLockChild.class, FileCache.class,
					Cache.class, CacheException.class)
					.map(type -> Path.of(URI.create(type.getProtectionDomain().getCodeSource().getLocation().toString())).toString())
					.distinct()
					.collect(Collectors.joining(File.pathSeparator));
			String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
			Path java = Path.of(System.getProperty("java.home"), "bin", executable);
			Process process = new ProcessBuilder(java.toString(), "-cp", classpath,
					WorkspaceSnapshotLockChild.class.getName(), cache.toString(), entry.toString(), key, version)
					.redirectErrorStream(true)
					.start();

			return new ChildPublisher(process,
					new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)),
					new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)));
		}

		private void publish() throws Exception {
			input.write("PUBLISH\n");
			input.flush();
		}

		@Override
		public void close() throws Exception {
			try (input; output) {
				if (process.isAlive()) process.destroyForcibly();
				assertTrue(process.waitFor(DEADLINE_SECONDS, TimeUnit.SECONDS), "Child publisher did not terminate");
			}
		}
	}
}
