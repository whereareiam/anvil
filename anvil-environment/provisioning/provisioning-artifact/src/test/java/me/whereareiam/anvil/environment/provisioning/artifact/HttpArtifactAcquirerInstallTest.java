package me.whereareiam.anvil.environment.provisioning.artifact;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpArtifactAcquirerInstallTest {
	@TempDir
	Path directory;

	@Test
	void installsOnceWhenScenariosPrepareOneInstallationAtOnce() throws Exception {
		Path target = directory.resolve("distributions/neoforge/21.1.252/server");
		AtomicInteger runs = new AtomicInteger();
		CountDownLatch start = new CountDownLatch(1);

		try (var first = acquirer(false);
		     var second = acquirer(false);
		     var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
			var installs = IntStream.range(0, 8).mapToObj(index -> tasks.submit(() -> {
				start.await();
				return (index % 2 == 0 ? first : second).install(target, staging -> {
					runs.incrementAndGet();
					sleep();
					Files.createDirectories(staging.resolve("libraries"));
					Files.writeString(staging.resolve("libraries/neoforge.jar"), "library");
					Files.writeString(staging.resolve("server.jar"), "starter");
				});
			})).toList();
			start.countDown();

			for (var install : installs)
				assertEquals(target, install.get(10, TimeUnit.SECONDS));
		}

		assertEquals(1, runs.get());
		assertEquals("starter", Files.readString(target.resolve("server.jar")));
		assertEquals("library", Files.readString(target.resolve("libraries/neoforge.jar")));
		assertEquals(List.of(target), siblings(target), "Only the published installation remains");
	}

	@Test
	void leavesNothingOfAFailedInstallationAndRunsTheNextOne() throws Exception {
		Path target = directory.resolve("distributions/neoforge/21.1.252/server");

		try (var artifacts = acquirer(false)) {
			ProvisioningException failure = assertThrows(ProvisioningException.class, () -> artifacts.install(target, staging -> {
				Files.writeString(staging.resolve("partial.jar"), "partial");
				throw new IOException("installer failed");
			}));
			assertEquals("installer failed", failure.getCause().getMessage());
			assertFalse(Files.exists(target));
			assertEquals(List.of(), siblings(target), "A failed installation must not leave staging data");

			artifacts.install(target, staging -> Files.writeString(staging.resolve("server.jar"), "starter"));
		}

		assertEquals("starter", Files.readString(target.resolve("server.jar")));
	}

	@Test
	void usesOnlyFinishedInstallationsOffline() throws Exception {
		Path installed = Files.createDirectories(directory.resolve("installed"));

		try (var offline = acquirer(true)) {
			assertEquals(installed, offline.install(installed, staging -> {
				throw new AssertionError("An installed directory is not installed again");
			}));
			ProvisioningException failure = assertThrows(ProvisioningException.class,
					() -> offline.install(directory.resolve("missing"), staging -> {
						throw new AssertionError("Offline installations must not run");
					}));
			assertTrue(failure.getMessage().startsWith("Installation is unavailable offline"), failure.getMessage());
		}
	}

	private HttpArtifactAcquirer acquirer(boolean offline) {
		return new HttpArtifactAcquirer(directory, new TestArtifactStorage(new FileCache(directory)), offline, false, 2);
	}

	private static List<Path> siblings(Path target) throws IOException {
		if (!Files.isDirectory(target.getParent())) return List.of();

		try (Stream<Path> entries = Files.list(target.getParent())) {
			return entries.toList();
		}
	}

	private static void sleep() throws IOException {
		try {
			Thread.sleep(200);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IOException(interrupted);
		}
	}
}
