package me.whereareiam.anvil.integration.intellij.scenario.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.tooling.ProjectToolingHost;
import me.whereareiam.anvil.integration.intellij.type.CatalogState;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;

public class EnvironmentExecutionPlatformTest extends EnginePlatformTestCase {
	private JsonNode lastStartRequest;

	public void testDiscoveryQueuesSavedSelectionUntilReadyAndStartsExecutionOnce() throws Exception {
		Path directory = prepareProject();
		ControlledProcess export = new ControlledProcess(false);
		ControlledProcess runner = new ControlledProcess(false);
		ProjectEnvironmentLifecycle session = session(export, runner);
		catalog().load(selectedProject(directory));
		assertTrue(export.started.await(5, TimeUnit.SECONDS));

		var environment = session.start(selectedProject(directory), scenario());
		var output = recorded(environment.getLog());

		export.complete(0);
		assertTrue(runner.started.await(5, TimeUnit.SECONDS));
		runner.emit("{\"type\":\"ready\",\"protocolVersion\":7}");

		JsonNode scenarios = runner.nextRequest();
		assertEquals("scenarios", scenarios.path("operation").asText());
		runner.emit(success(scenarios, "[{\"definition\":\"example.Definition\",\"name\":\"registration\",\"displayName\":\"Registration\"}]"));
		JsonNode start = runner.nextRequest();
		assertEquals("start", start.path("operation").asText());
		assertEquals("example.Definition", start.path("definition").asText());
		assertEquals("registration", start.path("scenario").asText());

		environment.stop();
		assertTrue(output.waitFor(5000));
		assertEquals(SessionState.STOPPED, environment.getSnapshot().getState());
		assertTrue(runner.requests.isEmpty());
		assertFalse(Files.exists(directory.resolve("tooling.json")));
	}

	public void testUserStopPreservesReportedCleanupFailure() throws Exception {
		Path directory = prepareProject();
		ControlledProcess export = new ControlledProcess(false);
		ControlledProcess runner = new ControlledProcess(true);
		ProjectEnvironmentLifecycle session = session(export, runner);
		var environment = session.start(selectedProject(directory), scenario());
		var output = recorded(environment.getLog());

		assertTrue(export.started.await(5, TimeUnit.SECONDS));
		export.complete(0);
		assertTrue(runner.started.await(5, TimeUnit.SECONDS));
		runner.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		runner.nextRequest();
		runner.nextRequest();

		environment.stop();
		assertTrue(output.waitFor(5000));
		assertEquals(SessionState.FAILED, environment.getSnapshot().getState());
		assertEquals("cleanup failed", environment.getSnapshot().getFailure());
	}

	public void testCancellingPreparationDoesNotReportAnEnvironmentCleanupFailure() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runtime = new ControlledProcess(false);
		ProjectEnvironmentLifecycle session = session(build, runtime);
		var environment = session.start(selectedProject(directory), scenario());
		var output = recorded(environment.getLog());

		assertTrue(build.started.await(5, TimeUnit.SECONDS));
		environment.stop();
		assertTrue(output.waitFor(5000));
		assertEquals(SessionState.STOPPED, environment.getSnapshot().getState());
		assertEquals(Integer.valueOf(0), output.getExitCode());
		assertEquals(1L, runtime.started.getCount());
		assertFalse(Files.exists(directory.resolve("tooling.json")));
	}

	public void testRefreshReplacesIdleRuntimeAndLoadsAFreshCatalog() throws Exception {
		Path directory = prepareProject();
		ControlledProcess firstBuild = new ControlledProcess(false);
		ControlledProcess firstRuntime = new ControlledProcess(false);
		ControlledProcess secondBuild = new ControlledProcess(false);
		ControlledProcess secondRuntime = new ControlledProcess(false);
		ProjectEnvironmentLifecycle session = session(firstBuild, firstRuntime, secondBuild, secondRuntime);
		var output = recorded(catalog().getLog());
		catalog().load(selectedProject(directory));
		assertTrue(firstBuild.started.await(5, TimeUnit.SECONDS));
		firstBuild.complete(0);
		assertTrue(firstRuntime.started.await(5, TimeUnit.SECONDS));
		firstRuntime.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		JsonNode scenarios = firstRuntime.nextRequest();
		assertEquals("scenarios", scenarios.path("operation").asText());
		firstRuntime.emit(success(scenarios, "[]"));
		await(() -> catalog().snapshot().getState() == CatalogState.READY);
		assertEquals(CatalogState.READY, catalog().snapshot().getState());

		catalog().load(selectedProject(directory));
		assertTrue(secondBuild.started.await(5, TimeUnit.SECONDS));
		assertFalse(firstRuntime.isAlive());
		assertEquals(2, output.getClearCount());
		assertEquals(CatalogState.LOADING, catalog().snapshot().getState());
		secondBuild.complete(0);
		assertTrue(secondRuntime.started.await(5, TimeUnit.SECONDS));
		secondRuntime.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		assertEquals("scenarios", secondRuntime.nextRequest().path("operation").asText());
		catalog().cancel();
		await(() -> getProject().getService(ProjectToolingHost.class).current().isFinished());
		assertFalse(Files.exists(directory.resolve("tooling.json")));
	}

	public void testSendReportsWriterSubmissionWithoutWaitingForRemoteResult() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runtime = new ControlledProcess(false);
		RetainedEnvironmentSession run = start(session(build, runtime), directory, build, runtime);
		assertSame(getProject(), run.getIdeProject());
		assertTrue(run.console("lobby", "list").get(5, TimeUnit.SECONDS));
		JsonNode request = runtime.nextRequest();
		assertEquals("console", request.path("operation").asText());
		assertEquals("lobby", request.path("target").asText());
		assertEquals("list", request.path("text").asText());
		assertFalse(outputText(run).contains("list"));
		run.stop();
		assertFalse(run.console("lobby", "late command").get(5, TimeUnit.SECONDS));
		assertTrue(recorded(run.getLog()).waitFor(5000));
		assertTrue(runtime.requests.isEmpty());
		Disposer.dispose(run);
		assertFalse(run.console("lobby", "disposed command").get(5, TimeUnit.SECONDS));
	}

	public void testSendReportsWriteFailureWithoutRetainingCommandText() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runtime = new ControlledProcess(false);
		RetainedEnvironmentSession run = start(session(build, runtime), directory, build, runtime);
		runtime.failWrites = true;
		assertFalse(run.console("lobby", "private command arguments").get(5, TimeUnit.SECONDS));
		await(() -> outputText(run).contains("Cannot send Anvil action"));
		assertFalse(outputText(run).contains("private command arguments"));
		assertTrue(runtime.requests.isEmpty());
		run.stop();
		assertTrue(recorded(run.getLog()).waitFor(5000));
	}

	public void testRejectedStartRetainsFailureAndTerminatesRun() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runtime = new ControlledProcess(false);
		RetainedEnvironmentSession run = start(session(build, runtime), directory, build, runtime);
		runtime.emit(failure(lastStartRequest, "Scenario no longer exists"));
		assertTrue(recorded(run.getLog()).waitFor(5000));
		assertEquals(SessionState.FAILED, run.getSnapshot().getState());
		assertEquals("Scenario no longer exists", run.getSnapshot().getFailure());
		assertFalse(run.isActive());
	}

	public void testSelectedStartRejectsOlderRuntimeBeforeSendingAnyStartCommand() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runtime = new ControlledProcess(false);
		ProjectEnvironmentLifecycle session = session(build, runtime);
		RetainedEnvironmentSession run = session.startProcess(selectedProject(directory), scenario(), "server");

		assertTrue(build.started.await(5, TimeUnit.SECONDS));
		build.complete(0);
		assertTrue(runtime.started.await(5, TimeUnit.SECONDS));
		runtime.emit("{\"type\":\"ready\",\"protocolVersion\":3}");
		assertTrue(recorded(run.getLog()).waitFor(5000));
		assertEquals(SessionState.FAILED, run.getSnapshot().getState());
		assertTrue(run.getSnapshot().getFailure().contains("Unsupported Anvil tooling protocol"));
		assertTrue(runtime.requests.isEmpty());
	}

	public void testStoppingAPartialRunClosesTheWholeRuntimeAndReleasesItsManifest()
			throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runtime = new ControlledProcess(false);
		ProjectEnvironmentLifecycle session = session(build, runtime);
		RetainedEnvironmentSession run = session.startProcess(selectedProject(directory), scenario(), "lobby");

		assertTrue(build.started.await(5, TimeUnit.SECONDS));
		build.complete(0);
		assertTrue(runtime.started.await(5, TimeUnit.SECONDS));
		runtime.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		assertEquals("scenarios", runtime.nextRequest().path("operation").asText());
		JsonNode start = runtime.nextRequest();
		assertEquals("start", start.path("operation").asText());
		assertEquals("lobby", start.path("target").asText());
		runtime.emit(
				"{\"type\":\"snapshot\",\"snapshot\":{\"state\":\"RUNNING\",\"processes\":["
						+ "{\"name\":\"lobby\",\"executionId\":\"00000000-0000-0000-0000-000000000001\",\"displayName\":\"Lobby\",\"state\":\"READY\",\"host\":\"127.0.0.1\",\"port\":25566,\"workDirectory\":\"/work/lobby\"}]}}");
		await(() -> run.getSnapshot().getProcesses().size() == 1);
		run.stop();
		assertTrue(recorded(run.getLog()).waitFor(5000));
		assertFalse(session.hasActiveSession());
		assertFalse(runtime.isAlive());
		assertEquals(SessionState.STOPPED, run.getSnapshot().getState());
		assertFalse(Files.exists(directory.resolve("tooling.json")));
	}

	public void testEnvironmentFailurePreservesTheLoadedCatalog() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runner = new ControlledProcess(false);
		var environments = session(build, runner);
		catalog().load(selectedProject(directory));
		assertTrue(build.started.await(5, TimeUnit.SECONDS));
		build.complete(0);
		assertTrue(runner.started.await(5, TimeUnit.SECONDS));
		runner.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		runner.emit(success(runner.nextRequest(), "[{\"definition\":\"example.Definition\",\"name\":\"registration\",\"displayName\":\"Registration\"}]"));
		await(() -> catalog().snapshot().getState() == CatalogState.READY);

		var environment = environments.start(selectedProject(directory), scenario());
		runner.emit(failure(runner.nextRequest(), "Environment startup failed"));
		await(() -> !environment.isActive());
		assertEquals(SessionState.FAILED, environment.getSnapshot().getState());
		assertEquals(CatalogState.READY, catalog().snapshot().getState());
		assertNull(catalog().snapshot().getFailure());
		assertEquals(1, catalog().snapshot().getScenarios().size());
	}

	public void testClosingAHandleKeepsTheLaunchReservedUntilCleanupFinishes() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runner = new ControlledProcess(false);
		var environments = session(build, runner);
		var environment = start(environments, directory, build, runner);
		runner.holdCleanup = true;

		Disposer.dispose(environment);
		await(() -> runner.closeRequested);
		assertTrue(environments.getSessions().isEmpty());
		assertNull(environments.getActiveSession());
		assertTrue(environments.hasActiveSession());
		try {
			catalog().load(selectedProject(directory));
			fail("Cleanup still owns the project launch");
		} catch (IllegalStateException expected) {
			assertTrue(runner.isAlive());
		}

		runner.complete(0);
		await(() -> !environments.hasActiveSession());
		assertEquals(SessionState.STOPPED, environment.getSnapshot().getState());
	}

	public void testMalformedHandshakeFailsDiscoveryAndEnvironmentAndCleansUp() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runner = new ControlledProcess(false);
		var environments = session(build, runner);
		var environment = environments.start(selectedProject(directory), scenario());
		assertTrue(build.started.await(5, TimeUnit.SECONDS));
		build.complete(0);
		assertTrue(runner.started.await(5, TimeUnit.SECONDS));

		runner.emit("{\"type\":\"ready\",\"protocolVersion\":\"7\"}");
		await(() -> !environment.isActive());
		assertEquals(SessionState.FAILED, environment.getSnapshot().getState());
		assertEquals(CatalogState.FAILED, catalog().snapshot().getState());
		assertTrue(runner.requests.isEmpty());
		assertFalse(Files.exists(directory.resolve("tooling.json")));
	}

	public void testReplacementWaitsForCleanupAndIgnoresLateDiscovery() throws Exception {
		Path directory = prepareProject();
		ControlledProcess firstBuild = new ControlledProcess(false);
		ControlledProcess firstRunner = new ControlledProcess(false);
		ControlledProcess secondBuild = new ControlledProcess(false);
		ControlledProcess secondRunner = new ControlledProcess(false);
		session(firstBuild, firstRunner, secondBuild, secondRunner);
		catalog().load(selectedProject(directory));
		assertTrue(firstBuild.started.await(5, TimeUnit.SECONDS));
		firstBuild.complete(0);
		assertTrue(firstRunner.started.await(5, TimeUnit.SECONDS));
		firstRunner.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		JsonNode oldRequest = firstRunner.nextRequest();
		firstRunner.holdCleanup = true;

		catalog().load(selectedProject(directory));
		await(() -> firstRunner.closeRequested);
		firstRunner.emit(success(oldRequest, "[{\"definition\":\"old.Definition\",\"name\":\"stale\",\"displayName\":\"Stale\"}]"));
		assertEquals(1L, secondBuild.started.getCount());
		assertEquals(CatalogState.LOADING, catalog().snapshot().getState());
		assertTrue(catalog().snapshot().getScenarios().isEmpty());

		firstRunner.complete(0);
		assertTrue(secondBuild.started.await(5, TimeUnit.SECONDS));
		secondBuild.complete(0);
		assertTrue(secondRunner.started.await(5, TimeUnit.SECONDS));
		secondRunner.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		secondRunner.emit(success(secondRunner.nextRequest(), "[]"));
		await(() -> catalog().snapshot().getState() == CatalogState.READY);
		assertTrue(catalog().snapshot().getScenarios().isEmpty());
		catalog().cancel();
		await(() -> getProject().getService(ProjectToolingHost.class).current().isFinished());
	}

	private RetainedEnvironmentSession start(
			ProjectEnvironmentLifecycle session,
			Path directory,
			ControlledProcess build,
			ControlledProcess runtime)
			throws Exception {
		RetainedEnvironmentSession run = session.start(selectedProject(directory), scenario());

		assertTrue(build.started.await(5, TimeUnit.SECONDS));
		build.complete(0);
		assertTrue(runtime.started.await(5, TimeUnit.SECONDS));
		runtime.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		JsonNode scenarios = runtime.nextRequest();
		assertEquals("scenarios", scenarios.path("operation").asText());
		lastStartRequest = runtime.nextRequest();
		assertEquals("start", lastStartRequest.path("operation").asText());
		return run;
	}

	private static String success(JsonNode request, String result) {
		return "{\"type\":\"response\",\"id\":\""
				+ request.path("id").asText()
				+ "\",\"success\":true,\"result\":"
				+ result
				+ "}";
	}

	private static String failure(JsonNode request, String error) {
		return "{\"type\":\"response\",\"id\":\""
				+ request.path("id").asText()
				+ "\",\"success\":false,\"error\":\""
				+ error
				+ "\"}";
	}

	private static ScenarioDescriptor scenario() {
		return ScenarioDescriptor.builder()
				.definition("example.Definition")
				.name("registration")
				.displayName("Registration")
				.build();
	}

	private String outputText(RetainedEnvironmentSession run) {
		return recorded(run.getLog()).text();
	}

	private void await(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
		assertTrue(condition.getAsBoolean());
	}

	private Path prepareProject() throws IOException {
		Path directory = Files.createTempDirectory("anvil-intellij-session-test-");
		Disposer.register(
				getTestRootDisposable(), () -> assertTrue(FileUtil.delete(directory.toFile())));
		ProjectBuildIntegrations.BUILD_INTEGRATIONS
				.getPoint()
				.registerExtension(
						new BuildIntegration() {
							@Override
							public @NotNull String getId() {
								return "fixture";
							}

							@Override
							public @NotNull SourceListing discover(@NotNull Project project) {
								return SourceListing.builder()
										.status(SourceListingStatus.READY)
										.sources(List.of(selectedProject(directory)))
											.message("Example scenario source detected")
										.build();
							}

							@Override
							public boolean canSync(@NotNull Project project) {
								return false;
							}

							@Override
							public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
								return CompletableFuture.failedFuture(
										new UnsupportedOperationException(
												"This fixture does not sync projects."));
							}

							@Override
							public void subscribe(
									@NotNull Project project,
									@NotNull Consumer<ProjectChange> listener,
									@NotNull Disposable owner) {}

							@Override
							public @NotNull ScenarioPreparation prepare(
									@NotNull Project project, @NotNull ScenarioSource selected) throws IOException {
								Path manifest = directory.resolve("tooling.json");
								Files.writeString(
										manifest,
										"""
										{"schemaVersion":1,"toolingJavaExecutable":"test-java","classpath":["test-runtime"],"definitions":["example.Definition"],"properties":{}}
										""");
								return ScenarioPreparation.builder()
										.command(List.of("fixture-prepare"))
										.workingDirectory(directory)
										.manifestPath(manifest)
										.build();
							}
						},
						getTestRootDisposable());
		return directory;
	}

	private ScenarioSource selectedProject(Path directory) {
		return ScenarioSource.builder()
				.id("fixture:main")
				.integrationId("fixture")
				.displayName("Example")
				.directory(directory)
				.build();
	}

	private ProjectEnvironmentLifecycle session(ControlledProcess... processes) {
		AtomicInteger launched = new AtomicInteger();
		ProjectEnvironmentLifecycle session =
				tooling(
						builder -> {
							ControlledProcess process = processes[launched.getAndIncrement()];
							process.started.countDown();
							return process;
						});
		Disposer.register(
				getTestRootDisposable(),
				() -> {
					for (ControlledProcess process : processes) process.complete(0);
				});
		Disposer.register(getTestRootDisposable(), session);
		return session;
	}

	private ScenarioCatalog catalog() {
		return getProject().getService(ScenarioCatalog.class);
	}

	private static final class ControlledProcess extends Process {
		private static final ObjectMapper JSON = new ObjectMapper();
		private final PipedInputStream output = new PipedInputStream();
		private final PrintWriter emitted;
		private final CountDownLatch started = new CountDownLatch(1);
		private final CountDownLatch terminated = new CountDownLatch(1);
		private final BlockingQueue<JsonNode> requests = new LinkedBlockingQueue<>();
		private final boolean cleanupFails;
		private volatile int exitCode;
		private volatile boolean failWrites;
		private volatile boolean holdCleanup;
		private volatile boolean closeRequested;

		private ControlledProcess(boolean cleanupFails) throws IOException {
			this.cleanupFails = cleanupFails;
			emitted = new PrintWriter(new PipedOutputStream(output), true, StandardCharsets.UTF_8);
		}

		private void emit(String line) {
			emitted.println(line);
		}

		private void complete(int code) {
			exitCode = code;
			emitted.close();
			terminated.countDown();
		}

		private JsonNode nextRequest() throws InterruptedException {
			JsonNode request = requests.poll(5, TimeUnit.SECONDS);
			assertNotNull("The tooling request was not sent", request);
			return request;
		}

		@Override
		public OutputStream getOutputStream() {
			return new OutputStream() {
				private final ByteArrayOutputStream line = new ByteArrayOutputStream();

				@Override
				public void write(int value) throws IOException {
					if (failWrites) throw new IOException("Controlled writer is closed");
					if (value != '\n') {
						line.write(value);
						return;
					}
					requests.add(JSON.readTree(line.toByteArray()));
					line.reset();
				}

				@Override
				public void close() {
					closeRequested = true;
					if (holdCleanup) return;
					emit(
							cleanupFails
									? "{\"type\":\"snapshot\",\"snapshot\":{\"state\":\"FAILED\",\"failure\":\"cleanup"
												+ " failed\"}}"
									: "{\"type\":\"snapshot\",\"snapshot\":{\"state\":\"STOPPED\"}}");
					complete(cleanupFails ? 1 : 0);
				}
			};
		}

		@Override
		public InputStream getInputStream() {
			return output;
		}

		@Override
		public InputStream getErrorStream() {
			return new ByteArrayInputStream(new byte[0]);
		}

		@Override
		public int waitFor() throws InterruptedException {
			terminated.await();
			return exitCode;
		}

		@Override
		public int exitValue() {
			if (isAlive()) throw new IllegalThreadStateException("Still running");
			return exitCode;
		}

		@Override
		public boolean isAlive() {
			return terminated.getCount() != 0;
		}

		@Override
		public void destroy() {
			complete(143);
		}
	}
}
