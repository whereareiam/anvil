package me.whereareiam.anvil.integration.intellij.tooling;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.ServiceContainerUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.tooling.process.ControlledToolingProcess;
import me.whereareiam.anvil.integration.intellij.tooling.process.ToolingConnection;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

public class ToolingLaunchPlatformTest extends EnginePlatformTestCase {
	private Path directory;
	private Path manifest;
	private ScenarioSource source;
	private boolean manifestDirectory;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		directory = Files.createTempDirectory("anvil-launch-lifecycle-");
		manifest = directory.resolve("tooling.json");
		source = ScenarioSource.builder().id("fixture:launch").integrationId("fixture")
				.displayName("Fixture").directory(directory).build();
		Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
		AccountLibrary accounts = getProject().getService(AccountLibrary.class);
		accounts.setDirectory(directory.resolve("accounts"));
		accounts.setIncludesGlobal(false);
		ServiceContainerUtil.replaceService(getProject(), BuildIntegrations.class, new PreparedSource(), getTestRootDisposable());
	}

	public void testSchedulingFailureCompletesAllFuturesWithoutAcquiringResources() throws Exception {
		AtomicInteger starts = new AtomicInteger();
		var launch = new ToolingLaunch(source, ToolingLaunch.Inputs.of(getProject()), builder -> {
			starts.incrementAndGet();
			throw new AssertionError("Rejected work must not start a process");
		}, task -> { throw new RejectedExecutionException("Worker unavailable"); });
		var notifications = new AtomicInteger();
		launch.outcome().thenAccept(result -> notifications.incrementAndGet());

		launch.startAfter(null);
		var result = completed(launch);

		assertInstanceOf(failure(result), RejectedExecutionException.class);
		assertEquals(ToolingLaunch.State.FINISHED, launch.state());
		assertTrue(launch.ready().isCompletedExceptionally());
		assertTrue(launch.discover().isCompletedExceptionally());
		assertEquals(0, starts.get());
		assertFalse(Files.exists(manifest));
		assertThrows(IllegalStateException.class, () -> launch.startAfter(null));
		assertEquals(1, notifications.get());
	}

	public void testPreparationFailureKeepsItsCauseAndSuppressesManifestCleanupFailure() throws Exception {
		manifestDirectory = true;
		ControlledToolingProcess build = process(false);
		build.complete(1);
		var launch = launch(builder -> build);
		launch.startAfter(null);

		var result = completed(launch);

		assertInstanceOf(failure(result), IOException.class);
		assertTrue(failure(result).getMessage().contains("Could not prepare scenarios"));
		assertEquals(1, failure(result).getSuppressed().length);
		assertInstanceOf(failure(result).getSuppressed()[0], DirectoryNotEmptyException.class);
		assertTrue(launch.ready().isCompletedExceptionally());
		assertTrue(launch.discover().isCompletedExceptionally());
	}

	public void testFailedRunnerAcquisitionReleasesCredentialsAndManifestBeforeCompletion() throws Exception {
		ControlledToolingProcess build = process(false);
		build.complete(0);
		AtomicReference<Path> accounts = new AtomicReference<>();
		IOException acquisition = new IOException("Cannot start the runner");
		var launch = launch(builder -> {
			if (builder.command().getFirst().equals("prepare")) return build;
			accounts.set(accountsDirectory(builder));
			throw acquisition;
		});
		var released = launch.outcome().thenApply(result -> !Files.exists(manifest) && !Files.exists(accounts.get()));
		launch.startAfter(null);

		assertSame(acquisition, failure(completed(launch)));
		assertTrue(released.get(5, TimeUnit.SECONDS));
	}

	public void testThrowingOutputAndFailureListenersCannotSkipCleanupOrOtherListeners() throws Exception {
		ControlledToolingProcess build = process(false);
		build.emit("Preparation output");
		build.emit("More preparation output");
		var launch = launch(builder -> build);
		RuntimeException outputFailure = new IllegalStateException("Output subscriber failed");
		RuntimeException failedListener = new IllegalStateException("Failure subscriber failed");
		AtomicInteger failures = new AtomicInteger();
		AtomicInteger outputs = new AtomicInteger();
		AtomicInteger rejectedOutputs = new AtomicInteger();
		launch.subscribe(new ToolingLaunch.Listener() {
			@Override
			public void output(@NotNull SessionLogEntry entry) {
				rejectedOutputs.incrementAndGet();
				throw outputFailure;
			}
		}, getTestRootDisposable());
		launch.subscribe(new ToolingLaunch.Listener() {
			@Override
			public void failed(@NotNull Throwable failure) {
				throw failedListener;
			}
		}, getTestRootDisposable());
		launch.subscribe(new ToolingLaunch.Listener() {
			@Override
			public void output(@NotNull SessionLogEntry entry) {
				outputs.incrementAndGet();
			}

			@Override
			public void failed(@NotNull Throwable failure) {
				failures.incrementAndGet();
			}
		}, getTestRootDisposable());
		launch.startAfter(null);

		var result = completed(launch);

		assertSame(outputFailure, failure(result));
		assertContainsElements(List.of(failure(result).getSuppressed()), failedListener);
		assertEquals(1, failures.get());
		assertEquals(2, outputs.get());
		assertEquals(1, rejectedOutputs.get());
		assertFalse(Files.exists(manifest));
		assertFalse(build.isAlive());
		assertTrue(launch.ready().isCompletedExceptionally());
		assertTrue(launch.discover().isCompletedExceptionally());
	}

	public void testStdoutFailureWaitsForFinalStderrBeforeReleasingFiles() throws Exception {
		ControlledToolingProcess build = process(false);
		build.complete(0);
		ControlledToolingProcess runner = process(true);
		var diagnostics = new DelayedDiagnostics();
		AtomicReference<Path> accounts = new AtomicReference<>();
		List<String> output = new CopyOnWriteArrayList<>();
		IOException stdoutFailure = new IOException("Runner output failed");
		Process streams = new StreamProcess(runner, new InputStream() {

			@Override
			public int read() throws IOException {
				throw stdoutFailure;
			}
		}, diagnostics);
		var launch = launch(builder -> {
			if (builder.command().getFirst().equals("prepare")) return build;
			accounts.set(accountsDirectory(builder));
			return streams;
		});
		launch.subscribe(new ToolingLaunch.Listener() {
			@Override
			public void output(@NotNull SessionLogEntry entry) {
				output.add(entry.getText());
			}
		}, getTestRootDisposable());
		launch.startAfter(null);
		try {
			assertTrue(diagnostics.reading.await(5, TimeUnit.SECONDS));
			assertTrue(runner.waitFor(5, TimeUnit.SECONDS));
			assertEquals(0, runner.exitValue());
			assertFalse(launch.outcome().isDone());
			assertTrue(Files.exists(manifest));
			assertTrue(Files.exists(accounts.get()));
		} finally {
			diagnostics.release.countDown();
		}

		var result = completed(launch);
		assertSame(stdoutFailure, failure(result));
		assertContainsElements(output, "Final diagnostic\n");
		assertTrue(diagnostics.closed);
		assertFalse(Files.exists(manifest));
		assertFalse(Files.exists(accounts.get()));
	}

	public void testCleanupContinuesAfterReaderCloseFailsAndPreservesAllFailures() throws Exception {
		ControlledToolingProcess build = process(false);
		build.complete(0);
		ControlledToolingProcess runner = process(true);
		runner.complete(0);
		IOException readerFailure = new IOException("Cannot close runner diagnostics");
		AtomicReference<Path> accounts = new AtomicReference<>();
		Process streams = new StreamProcess(runner,
				new ByteArrayInputStream(("{\"type\":\"ready\",\"protocolVersion\":" + ToolingSession.PROTOCOL_VERSION + "}\n")
						.getBytes(StandardCharsets.UTF_8)),
				new ByteArrayInputStream(new byte[0]) {

					@Override
					public void close() throws IOException {
						throw readerFailure;
					}
				});
		var launch = launch(builder -> {
			if (builder.command().getFirst().equals("prepare")) return build;
			accounts.set(accountsDirectory(builder));
			Files.delete(manifest);
			Files.createDirectory(manifest);
			Files.writeString(manifest.resolve("keep"), "Cannot delete a nonempty manifest directory");
			return streams;
		});
		launch.startAfter(null);

		var result = completed(launch);

		assertSame(readerFailure, failure(result));
		assertEquals(1, readerFailure.getSuppressed().length);
		assertInstanceOf(readerFailure.getSuppressed()[0], DirectoryNotEmptyException.class);
		assertFalse(Files.exists(accounts.get()));
	}

	public void testDiagnosticFailureSettlesPendingFuturesBeforeStopCallbacks() throws Exception {
		ControlledToolingProcess build = process(false);
		build.complete(0);
		ControlledToolingProcess runner = process(true);
		IOException diagnosticFailure = new IOException("Diagnostic stream failed");
		Process streams = new StreamProcess(runner, runner.getInputStream(), new InputStream() {
			@Override
			public int read() throws IOException {
				throw diagnosticFailure;
			}
		});
		var launch = launch(builder -> builder.command().getFirst().equals("prepare") ? build : streams);
		launch.subscribe(new ToolingLaunch.Listener() {
			@Override
			public void snapshot(@NotNull SessionSnapshot snapshot) {
				if (snapshot.getState() == SessionState.STOPPED) launch.stop();
			}
		}, getTestRootDisposable());
		launch.startAfter(null);

		assertSame(diagnosticFailure, failure(completed(launch)));
		var readiness = Assertions.assertThrows(ExecutionException.class,
				() -> launch.ready().get(5, TimeUnit.SECONDS));
		var discovery = Assertions.assertThrows(ExecutionException.class,
				() -> launch.discover().get(5, TimeUnit.SECONDS));
		assertSame(diagnosticFailure, readiness.getCause());
		assertSame(diagnosticFailure, discovery.getCause());
		assertFalse(Files.exists(manifest));
	}

	public void testStopDuringPreparationEndsStoppedWithoutStartingTheRunner() throws Exception {
		ControlledToolingProcess build = process(false);
		AtomicInteger runners = new AtomicInteger();
		var launch = launch(builder -> {
			if (builder.command().getFirst().equals("prepare")) return build;
			runners.incrementAndGet();
			throw new AssertionError("A stopped launch must not start its runner");
		});
		launch.startAfter(null);
		await(() -> launch.state() == ToolingLaunch.State.PREPARING);

		launch.stop();
		var result = completed(launch);

		assertInstanceOf(result, ToolingLaunch.Outcome.Stopped.class);
		assertFalse(((ToolingLaunch.Outcome.Stopped) result).runnerStarted());
		assertTrue(result.cleanExit());
		assertEquals(0, runners.get());
		assertFalse(Files.exists(manifest));
		assertTrue(launch.ready().isCompletedExceptionally());
	}

	public void testStopAfterReadyEndsStoppedWithACleanRunnerExit() throws Exception {
		ControlledToolingProcess runner = startedRunner();
		var launch = launch(runnerStarter(runner));
		launch.startAfter(null);
		launch.ready().get(5, TimeUnit.SECONDS);
		assertEquals(ToolingLaunch.State.READY, launch.state());

		launch.stop();
		var result = completed(launch);

		assertInstanceOf(result, ToolingLaunch.Outcome.Stopped.class);
		assertTrue(((ToolingLaunch.Outcome.Stopped) result).runnerStarted());
		assertTrue(result.cleanExit());
		assertEquals(ToolingLaunch.State.FINISHED, launch.state());
		assertFalse(Files.exists(manifest));
	}

	public void testRunnerExitingWithAnErrorCodeFailsTheLaunch() throws Exception {
		ControlledToolingProcess runner = startedRunner();
		var launch = launch(runnerStarter(runner));
		launch.startAfter(null);
		launch.ready().get(5, TimeUnit.SECONDS);

		runner.complete(3);
		var result = completed(launch);

		assertTrue(failure(result).getMessage().contains("exited with code 3"));
		assertFalse(result.cleanExit());
		assertEquals(1, result.exitCode());
	}

	public void testDiscoveryIsRequestedOnceAndOnlyWhenAsked() throws Exception {
		ControlledToolingProcess runner = startedRunner();
		var launch = launch(runnerStarter(runner));
		launch.startAfter(null);
		launch.ready().get(5, TimeUnit.SECONDS);
		assertTrue(runner.getRequests().isEmpty());

		var first = launch.discover();
		var second = launch.discover();

		assertEquals("example", first.get(5, TimeUnit.SECONDS).getFirst().getName());
		assertEquals(first.get(), second.get(5, TimeUnit.SECONDS));
		assertEquals(1, runner.getRequests().size());
	}

	public void testFinishedLaunchReleasesItsListenersFromALongLivedOwner() throws Exception {
		Disposable owner = Disposer.newDisposable("Long-lived listener owner");
		Disposer.register(getTestRootDisposable(), owner);
		ControlledToolingProcess runner = startedRunner();
		var launch = launch(runnerStarter(runner));
		launch.subscribe(new ToolingLaunch.Listener() {}, owner);
		launch.startAfter(null);
		launch.ready().get(5, TimeUnit.SECONDS);
		assertEquals(1, children(owner));

		launch.stop();
		completed(launch);

		assertEquals(0, children(owner));
		launch.subscribe(new ToolingLaunch.Listener() {}, owner);
		assertEquals(0, children(owner));
	}

	public void testReplacementPreparesOnlyAfterItsPredecessorFinished() throws Exception {
		ControlledToolingProcess firstBuild = process(false);
		var first = launch(builder -> firstBuild);
		first.startAfter(null);
		await(() -> first.state() == ToolingLaunch.State.PREPARING);

		List<String> order = new CopyOnWriteArrayList<>();
		first.outcome().thenRun(() -> order.add("previous finished"));
		ControlledToolingProcess secondBuild = process(false);
		secondBuild.complete(1);
		var second = launch(builder -> {
			order.add("replacement prepared");
			return secondBuild;
		});
		second.startAfter(first);
		assertEquals(ToolingLaunch.State.WAITING, second.state());

		first.stop();
		completed(second);

		assertEquals(List.of("previous finished", "replacement prepared"), order);
	}

	private ToolingLaunch launch(ToolingConnection.Starter starter) {
		var launch = new ToolingLaunch(source, ToolingLaunch.Inputs.of(getProject()), starter);
		Disposer.register(getTestRootDisposable(), launch::stop);
		return launch;
	}

	private ControlledToolingProcess process(boolean runner) {
		var process = new ControlledToolingProcess(runner);
		Disposer.register(getTestRootDisposable(), () -> process.complete(0));
		return process;
	}

	private ControlledToolingProcess startedRunner() {
		ControlledToolingProcess runner = process(true);
		runner.emit("{\"type\":\"ready\",\"protocolVersion\":" + ToolingSession.PROTOCOL_VERSION + "}");

		return runner;
	}

	private ToolingConnection.Starter runnerStarter(ControlledToolingProcess runner) {
		ControlledToolingProcess build = process(false);
		build.complete(0);

		return builder -> builder.command().getFirst().equals("prepare") ? build : runner;
	}

	private static int children(Disposable owner) {
		AtomicInteger count = new AtomicInteger();
		Disposer.disposeChildren(owner, child -> {
			count.incrementAndGet();
			return false;
		});

		return count.get();
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > deadline) fail("Condition was not reached in time");
			Thread.sleep(5);
		}
	}

	private static Path accountsDirectory(ProcessBuilder builder) {
		String prefix = "-Danvil.accountsDir=";
		return builder.command().stream().filter(value -> value.startsWith(prefix))
				.map(value -> Path.of(value.substring(prefix.length()))).findFirst().orElseThrow();
	}

	private static ToolingLaunch.Outcome completed(ToolingLaunch launch) throws Exception {
		return launch.outcome().get(5, TimeUnit.SECONDS);
	}

	private static Throwable failure(ToolingLaunch.Outcome outcome) {
		assertInstanceOf(outcome, ToolingLaunch.Outcome.Failed.class);

		return ((ToolingLaunch.Outcome.Failed) outcome).cause();
	}

	private final class PreparedSource implements BuildIntegrations {

		@Override
		public boolean canSync() {
			return false;
		}

		@Override
		public @NotNull CompletableFuture<Void> sync() {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void subscribe(@NotNull Consumer<ProjectChange> listener, @NotNull Disposable owner) {}

		@Override
		public @NotNull SourceListing discover() {
			return SourceListing.builder().sources(List.of(source)).status(SourceListingStatus.READY).message("Ready").build();
		}

		@Override
		public @NotNull ScenarioSource resolve(@NotNull String id) {
			return source;
		}

		@Override
		public @NotNull ScenarioPreparation prepare(@NotNull ScenarioSource source) throws IOException {
			if (manifestDirectory) {
				Files.createDirectory(manifest);
				Files.writeString(manifest.resolve("keep"), "Cannot delete a nonempty manifest directory");
				return preparation();
			}
			Files.writeString(manifest,
					"{\"schemaVersion\":1,\"toolingJavaExecutable\":\"runner\",\"classpath\":[\"fixture.jar\"],\"definitions\":[]}");
			return preparation();
		}
		private ScenarioPreparation preparation() {
			return ScenarioPreparation.builder().command(List.of("prepare")).workingDirectory(directory).manifestPath(manifest).build();
		}
	}

	private static final class DelayedDiagnostics extends InputStream {
		private final CountDownLatch reading = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final InputStream text = new ByteArrayInputStream("Final diagnostic\n".getBytes(StandardCharsets.UTF_8));
		private volatile boolean closed;

		@Override
		public int read() throws IOException {
			reading.countDown();
			try {
				if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Diagnostic reader was not released");
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				throw new IOException(failure);
			}
			return text.read();
		}

		@Override
		public void close() {
			closed = true;
		}
	}

	@RequiredArgsConstructor
	private static final class StreamProcess extends Process {
		private final Process delegate;
		private final InputStream output;
		private final InputStream errors;

		@Override

		public OutputStream getOutputStream() {

			return delegate.getOutputStream();

		}

		@Override
		public InputStream getInputStream() {
			return output;
		}

		@Override
		public InputStream getErrorStream() {
			return errors;
		}

		@Override
		public int waitFor() throws InterruptedException {
			return delegate.waitFor();
		}

		@Override
		public int exitValue() {
			return delegate.exitValue();
		}

		@Override
		public boolean isAlive() {
			return delegate.isAlive();
		}

		@Override
		public void destroy() {
			delegate.destroy();
		}
	}
}
