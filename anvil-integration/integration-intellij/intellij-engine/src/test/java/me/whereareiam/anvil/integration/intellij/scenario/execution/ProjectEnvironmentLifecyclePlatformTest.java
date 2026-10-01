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
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.type.CatalogState;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;

public class ProjectEnvironmentLifecyclePlatformTest extends EnginePlatformTestCase {
	public void testProjectServiceIsAvailableBeforeOpeningTheToolWindow() {
		ScenarioCatalog catalog = getProject().getService(ScenarioCatalog.class);
		EnvironmentLifecycle environments = getProject().getService(EnvironmentLifecycle.class);

		assertNotNull(catalog);
		assertNotNull(environments);
		assertEquals(CatalogState.NOT_LOADED, catalog.snapshot().getState());
		assertFalse(environments.hasActiveSession());
	}

	public void testCatalogAndEnvironmentOwnersShareOnePreparedLaunch() throws Exception {
		Path directory = prepareProject();
		ControlledProcess build = new ControlledProcess(false);
		ControlledProcess runtime = new ControlledProcess(false);
		ProjectEnvironmentLifecycle projectRuntime = session(build, runtime);
		ScenarioCatalog catalog = getProject().getService(ScenarioCatalog.class);
		EnvironmentLifecycle environments = getProject().getService(EnvironmentLifecycle.class);
		ScenarioSource source = selectedProject(directory);

		catalog.load(source);
		assertTrue(build.started.await(5, TimeUnit.SECONDS));
		build.complete(0);
		assertTrue(runtime.started.await(5, TimeUnit.SECONDS));
		runtime.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		JsonNode scenarios = runtime.nextRequest();
		assertEquals("scenarios", scenarios.path("operation").asText());
		runtime.emit(success(scenarios, "[{\"definition\":\"example.Definition\",\"name\":\"registration\",\"displayName\":\"Registration\"}]"));
		await(() -> catalog.snapshot().getState() == CatalogState.READY);

		EnvironmentSession environment = environments.start(source, scenario());
		assertEquals("start", runtime.nextRequest().path("operation").asText());
		assertSame(environment, environments.getActiveSession());
		environment.stop();
	}

	public void testRetainedRunCannotControlNewRunAndKeepsItsOwnOutput() throws Exception {
		Path directory = prepareProject();
		ControlledProcess firstBuild = new ControlledProcess(false);
		ControlledProcess firstRuntime = new ControlledProcess(false);
		ControlledProcess secondBuild = new ControlledProcess(false);
		ControlledProcess secondRuntime = new ControlledProcess(false);
		ProjectEnvironmentLifecycle session = session(firstBuild, firstRuntime, secondBuild, secondRuntime);
		RetainedEnvironmentSession first = start(session, directory, firstBuild, firstRuntime);
		try {
			session.start(selectedProject(directory), scenario());
			fail("Only one environment may run in the project");
		} catch (IllegalStateException expected) {
			assertEquals(1, session.getSessions().size());
		}
		firstRuntime.emit(
				"{\"type\":\"log\",\"sessionId\":\"fixture\",\"process\":\"lobby\",\"executionId\":\"00000000-0000-0000-0000-000000000001\",\"sequence\":1,\"text\":\"First"
						+ " run log\"}");
		firstRuntime.emit(
				"{\"type\":\"snapshot\",\"snapshot\":{\"state\":\"RUNNING\",\"displayName\":\"Registration\"}}");
		await(() -> first.getSnapshot().getState() == SessionState.RUNNING);
		first.stop();
		await(() -> !session.hasActiveSession());
		assertEquals(SessionState.STOPPED, first.getSnapshot().getState());
		assertTrue(recorded(first.getLog()).waitFor(5000));
		assertFalse(first.isActive());
		String retainedOutput = outputText(first);
		assertTrue(retainedOutput.contains("First run log"));

		RetainedEnvironmentSession second = start(session, directory, secondBuild, secondRuntime);
		first.stop();
		assertFalse(
				first.console("lobby", "must not reach the newer run").get(5, TimeUnit.SECONDS));
		assertTrue(second.isActive());
		assertTrue(secondRuntime.requests.isEmpty());
		assertEquals(SessionState.STOPPED, first.getSnapshot().getState());
		assertEquals(retainedOutput, outputText(first));
		assertFalse(outputText(second).contains("First run log"));
		assertEquals(2, session.getSessions().size());
		Disposer.dispose(first);
		assertTrue(second.isActive());
		assertEquals(List.of(second), session.getSessions());
		Disposer.dispose(second);
		assertTrue(recorded(second.getLog()).waitFor(5000));
		assertFalse(session.hasActiveSession());
		assertFalse(Files.exists(directory.resolve("tooling.json")));
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
		assertEquals("scenarios", runtime.nextRequest().path("operation").asText());
		assertEquals("start", runtime.nextRequest().path("operation").asText());
		return run;
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

	private static String success(JsonNode request, String result) {
		return "{\"type\":\"response\",\"id\":\""
				+ request.path("id").asText()
				+ "\",\"success\":true,\"result\":"
				+ result
				+ "}";
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
