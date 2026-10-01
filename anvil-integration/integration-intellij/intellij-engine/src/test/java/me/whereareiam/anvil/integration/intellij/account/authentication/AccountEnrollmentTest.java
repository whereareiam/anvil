package me.whereareiam.anvil.integration.intellij.account.authentication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.tooling.process.ControlledToolingProcess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class AccountEnrollmentTest {
	@TempDir Path directory;

	@Test void streamsDeviceInstructionsBeforeExitAndCleansAfterCancellation() throws Exception {
		Path manifest = manifest();
		var build = new ControlledToolingProcess(false);
		build.complete(0);
		var login = new ControlledToolingProcess(false);
		var processes = new ArrayDeque<>(List.of(build, login));
		var received = new CountDownLatch(1);
		var output = new CopyOnWriteArrayList<String>();
		var enrollment = new ToolingAccountEnrollment(() -> plan(manifest), directory, "test", line -> {
			output.add(line);
			if (line.startsWith("Open https")) received.countDown();
		}, builder -> processes.removeFirst());
		try (var executor = Executors.newSingleThreadExecutor()) {
			var completion = enrollment.start(executor);
			login.emit("Open https://example.test/device?code=fixture");
			assertTrue(received.await(5, TimeUnit.SECONDS));
			assertTrue(login.isAlive());
			assertFalse(completion.isDone());
			enrollment.dispose();
			assertThrows(CancellationException.class, () -> completion.get(5, TimeUnit.SECONDS));
			assertFalse(login.isAlive());
			assertFalse(Files.exists(manifest));
		} finally { enrollment.dispose(); }
	}

	@Test void removesManifestWhenPreparationFails() throws Exception {
		Path manifest = manifest();
		var build = new ControlledToolingProcess(false);
		build.complete(1);
		var enrollment =
				new ToolingAccountEnrollment(
						() -> plan(manifest), directory, "test", ignored -> {}, builder -> build);
		var completion = enrollment.start(Runnable::run);
		assertTrue(completion.isCompletedExceptionally());
		assertFalse(Files.exists(manifest));
	}

	@Test void startIsSingleUseAndReturnsTheSameCompletion() throws Exception {
		Path manifest = manifest();
		var build = new ControlledToolingProcess(false);
		build.complete(0);
		var login = new ControlledToolingProcess(false);
		login.complete(0);
		var processes = new ArrayDeque<>(List.of(build, login));
		var enrollment = new ToolingAccountEnrollment(
				() -> plan(manifest),
				directory,
				"test",
				ignored -> {},
				ignored -> processes.removeFirst());

		var first = enrollment.start(Runnable::run);
		var second = enrollment.start(Runnable::run);

		assertSame(first, second);
		first.get(5, TimeUnit.SECONDS);
		assertTrue(processes.isEmpty());
		assertFalse(Files.exists(manifest));
	}

	@Test void disposingBeforeStartCompletesWithCancellation() throws Exception {
		var enrollment = new ToolingAccountEnrollment(() -> plan(manifest()), directory, "test", ignored -> {});
		enrollment.dispose();

		assertThrows(
				CancellationException.class,
				() -> enrollment.start(Runnable::run).get(5, TimeUnit.SECONDS));
	}

	@Test void cancellationDuringPlanningStillReleasesTheReturnedManifest() throws Exception {
		Path manifest = manifest();
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var launchAttempted = new AtomicBoolean();
		var enrollment =
					new ToolingAccountEnrollment(
							() -> {
								started.countDown();
								release.await();
								return plan(manifest);
						},
						directory,
						"test",
						ignored -> {},
						builder -> {
							launchAttempted.set(true);
							throw new IOException("Cancelled plan must not launch");
						});
		try (var executor = Executors.newSingleThreadExecutor()) {
			var completion = enrollment.start(executor);
			assertTrue(started.await(30, TimeUnit.SECONDS));
			enrollment.dispose();
			release.countDown();
			assertThrows(CancellationException.class, () -> completion.get(5, TimeUnit.SECONDS));
			assertFalse(launchAttempted.get());
			assertFalse(Files.exists(manifest));
		} finally { release.countDown(); enrollment.dispose(); }
	}

	private Path manifest() throws Exception {
		return Files.writeString(directory.resolve("manifest.json"),
				"{\"schemaVersion\":1,\"toolingJavaExecutable\":\"java\",\"classpath\":[\"fixture.jar\"],\"definitions\":[],\"properties\":{}}");
	}

	private ScenarioPreparation plan(Path manifest) {
		return ScenarioPreparation.builder().command(List.of("prepare")).workingDirectory(directory).manifestPath(manifest).build();
	}
}
