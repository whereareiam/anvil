package me.whereareiam.anvil.integration.intellij.scenario.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.Getter;
import me.whereareiam.anvil.integration.intellij.ControlledToolingProcess;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A scenario source whose preparation and tooling runner run in-process, for tests that start real launches.
 * <p>
 * Preparation writes a manifest and finishes at once; each runner reports itself ready and answers scenario
 * discovery with {@link #setScenariosJson(String)}. Every runner is completed when the test ends.
 */
public final class ControlledProject implements BuildIntegration {
	private final @NotNull Path directory;
	private final AtomicReference<List<ScenarioSource>> sources;
	@Getter private final List<ControlledToolingProcess> runners = new CopyOnWriteArrayList<>();
	@Getter private final List<ScenarioSource> prepared = new CopyOnWriteArrayList<>();
	private volatile String scenariosJson = "[]";

	private ControlledProject(@NotNull Path directory, @NotNull List<ScenarioSource> sources) {
		this.directory = directory;
		this.sources = new AtomicReference<>(sources);
	}

	/**
	 * Installs a project with the given source names as its only build integration.
	 *
	 * @param tooling replaces the test's tooling host with one that uses the given process starter
	 */
	public static @NotNull ControlledProject install(
			@NotNull Disposable owner,
			@NotNull Function<ToolingConnection.Starter, EnvironmentLifecycle> tooling,
			@NotNull String... names
	) throws IOException {
		Path directory = Files.createTempDirectory("anvil-controlled-project-");
		Disposer.register(owner, () -> FileUtil.delete(directory.toFile()));
		List<ScenarioSource> sources = java.util.Arrays.stream(names).map(name -> ScenarioSource.builder()
				.id("fixture:" + name)
				.displayName(name)
				.integrationId("fixture")
				.directory(directory.resolve(name))
				.build()).toList();
		var project = new ControlledProject(directory, sources);
		ExtensionTestUtil.maskExtensions(ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(project), owner);
		tooling.apply(project::start);
		Disposer.register(owner, () -> project.runners.forEach(runner -> runner.complete(0)));

		return project;
	}

	/**
	 * Returns the installed source with the given name.
	 */
	public @NotNull ScenarioSource source(@NotNull String name) {
		return sources.get().stream().filter(source -> source.getDisplayName().equals(name)).findFirst().orElseThrow();
	}

	/**
	 * Replaces the sources discovery reports, such as after a module was removed.
	 */
	public void setSources(@NotNull List<ScenarioSource> available) {
		sources.set(List.copyOf(available));
	}

	/**
	 * Sets the scenario descriptors every later runner reports, as a JSON array.
	 */
	public void setScenariosJson(@NotNull String json) {
		scenariosJson = json;
	}

	/**
	 * Returns the start requests the latest runner received.
	 */
	public @NotNull List<JsonNode> startRequests() {
		if (runners.isEmpty()) return List.of();

		return runners.getLast().getRequests().stream()
				.filter(request -> request.path("operation").asText().equals("start"))
				.toList();
	}

	/**
	 * Dispatches IDE events until the condition holds, failing after five seconds.
	 */
	public static void await(@NotNull BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
			Thread.sleep(5);
		}
		if (!condition.getAsBoolean()) throw new AssertionError("The awaited condition was not reached");
	}

	private @NotNull Process start(@NotNull ProcessBuilder builder) {
		boolean preparation = builder.command().getFirst().equals("fixture-prepare");
		var process = new ControlledToolingProcess(!preparation);
		if (preparation) {
			process.complete(0);
			return process;
		}

		process.setScenariosJson(scenariosJson);
		runners.add(process);
		process.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
		return process;
	}

	@Override
	public @NotNull String getId() {
		return "fixture";
	}

	@Override
	public @NotNull SourceListing discover(@NotNull Project project) {
		return SourceListing.builder().sources(sources.get()).status(SourceListingStatus.READY).message("Ready").build();
	}

	@Override
	public boolean canSync(@NotNull Project project) {
		return false;
	}

	@Override
	public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException("The controlled project does not sync."));
	}

	@Override
	public void subscribe(@NotNull Project project, @NotNull Consumer<ProjectChange> listener, @NotNull Disposable owner) {}

	@Override
	public @NotNull ScenarioPreparation prepare(@NotNull Project project, @NotNull ScenarioSource selected) throws IOException {
		prepared.add(selected);
		Path manifest = Files.createTempFile(directory, "anvil-tooling-", ".json");
		Files.writeString(manifest, "{\"schemaVersion\":1,\"toolingJavaExecutable\":\"fixture-java\","
				+ "\"classpath\":[\"fixture-runtime\"],\"definitions\":[]}");

		return ScenarioPreparation.builder()
				.command(List.of("fixture-prepare"))
				.workingDirectory(directory)
				.manifestPath(manifest)
				.build();
	}
}
