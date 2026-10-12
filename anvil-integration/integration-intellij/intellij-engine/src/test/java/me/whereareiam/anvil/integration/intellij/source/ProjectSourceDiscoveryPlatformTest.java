package me.whereareiam.anvil.integration.intellij.source;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.execution.ProjectEnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.tooling.process.ControlledToolingProcess;
import me.whereareiam.anvil.integration.intellij.type.CatalogState;
import me.whereareiam.anvil.integration.intellij.type.source.DiscoveryState;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;

public class ProjectSourceDiscoveryPlatformTest extends EnginePlatformTestCase {
	private PersistentPreferences settings;
	private ProjectEnvironmentLifecycle session;
	private CatalogProvider provider;
	private final List<ControlledToolingProcess> exports = new CopyOnWriteArrayList<>();
	private final List<ControlledToolingProcess> runners = new CopyOnWriteArrayList<>();
	private boolean finishExports = true;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		settings = new PersistentPreferences();
		ServiceContainerUtil.replaceService(
				ApplicationManager.getApplication(),
				Preferences.class,
				settings,
				getTestRootDisposable());
		Path directory = Files.createTempDirectory("anvil-catalog-refresh-");
		Disposer.register(
				getTestRootDisposable(), () -> assertTrue(FileUtil.delete(directory.toFile())));
		provider = new CatalogProvider(directory);
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(provider), getTestRootDisposable());
		ServiceContainerUtil.replaceService(
				getProject(),
				BuildIntegrations.class,
				new ProjectBuildIntegrations(getProject()),
				getTestRootDisposable());
		Disposer.register(
				getTestRootDisposable(),
				() -> {
					exports.forEach(process -> process.complete(0));
					runners.forEach(process -> process.complete(0));
				});
		session =
				tooling(
						builder -> {
							boolean export = builder.command().getFirst().equals("fixture-prepare");
							ControlledToolingProcess process = new ControlledToolingProcess(!export);
							if (export) {
								exports.add(process);
								if (finishExports) process.complete(0);
							} else {
								runners.add(process);
								process.emit("{\"type\":\"ready\",\"protocolVersion\":7}");
							}
							return process;
						});
		Disposer.register(getTestRootDisposable(), session);
	}

	public void testSuccessfulImportRefreshesIdleCatalogOnceAndDoesNotSyncAgain()
			throws Exception {
		provider.notifyDuringPreparation = true;
		discovery();
		awaitLoaded(1);
		provider.imported(2, 1);
		awaitLoaded(2);
		provider.changed(ProjectChange.IMPORTED);
		provider.changed(ProjectChange.IMPORTED);
		applyLatestImport();
		assertEquals(2, provider.prepared.size());
		assertEquals(2, getProject().getService(ScenarioCatalog.class).snapshot().getSource().getImportRevision());
		assertEquals(0, provider.syncs.get());
	}

	public void testImportsDuringPreparationCoalesceWithoutReplacingTheCurrentBuild()
			throws Exception {
		finishExports = false;
		discovery();
		await(() -> exports.size() == 1);
		ControlledToolingProcess preparing = exports.getFirst();
		provider.imported(2, 1);
		provider.imported(3, 1);
		applyLatestImport();
		assertEquals(1, exports.size());
		assertTrue(preparing.isAlive());
		preparing.complete(0);
		await(() -> exports.size() == 2);
		assertEquals(3, provider.prepared.getLast().getImportRevision());
		exports.getLast().complete(0);
		awaitLoaded(2);
		provider.changed(ProjectChange.IMPORTED);
		applyLatestImport();
		assertEquals(2, exports.size());
	}

	public void testImportsDuringRunWaitForCleanupAndRefreshOnlyLatestSelectedModule()
			throws Exception {
		discovery();
		awaitLoaded(1);
		RetainedEnvironmentSession run = startRun();
		ControlledToolingProcess running = runners.getLast();
		provider.imported(2, 1);
		provider.imported(3, 1);
		applyLatestImport();
		assertSame(run, session.getActiveSession());
		assertEquals(SessionState.RUNNING, run.getSnapshot().getState());
		assertEquals(1, exports.size());
		assertEquals(1, running.getStarts().get());
		running.setHoldCleanup(true);
		run.stop();
		await(() -> running.isCloseRequested());
		assertTrue(session.hasActiveSession());
		assertEquals(1, exports.size());
		running.complete(0);
		awaitLoaded(2);
		assertFalse(session.hasActiveSession());
		assertEquals("fixture:first", provider.prepared.getLast().getId());
		assertEquals(3, provider.prepared.getLast().getImportRevision());
		assertEquals(1, running.getStarts().get());
	}

	public void testDisabledPreferenceKeepsInitialDiscoveryAndExplicitRefresh() throws Exception {
		settings.setRefreshCatalogAfterSync(false);
		SourceDiscovery discovery = discovery();
		awaitLoaded(1);
		provider.imported(2, 1);
		applyLatestImport();
		assertEquals(1, exports.size());
		discovery.refresh();
		awaitLoaded(2);
		assertEquals(2, getProject().getService(ScenarioCatalog.class).snapshot().getSource().getImportRevision());
		provider.imported(3, 1);
		applyLatestImport();
		settings.setRefreshCatalogAfterSync(true);
		assertEquals(2, exports.size());
		provider.imported(4, 1);
		awaitLoaded(3);
		assertEquals(4, getProject().getService(ScenarioCatalog.class).snapshot().getSource().getImportRevision());
	}

	public void testTurningOffPreferenceCancelsRefreshDeferredUntilAfterRun() throws Exception {
		discovery();
		awaitLoaded(1);
		RetainedEnvironmentSession run = startRun();
		provider.imported(2, 1);
		applyLatestImport();
		settings.setRefreshCatalogAfterSync(false);
		run.stop();
		await(() -> !session.hasActiveSession());
		applyLatestImport();
		assertEquals(1, exports.size());
		settings.setRefreshCatalogAfterSync(true);
		assertEquals(1, exports.size());
	}

	public void testOtherModuleImportDoesNotReloadSelectionAndModuleSwitchUsesLatestRevision()
			throws Exception {
		SourceDiscovery discovery = discovery();
		awaitLoaded(1);
		provider.imported(1, 2);
		applyLatestImport();
		assertEquals(1, exports.size());
		provider.imported(2, 3);
		awaitLoaded(2);
		assertEquals("fixture:first", provider.prepared.getLast().getId());
		discovery.select(discovery.snapshot().getListing().getSources().get(1));
		awaitLoaded(3);
		assertEquals("fixture:second", provider.prepared.getLast().getId());
		assertEquals(3, provider.prepared.getLast().getImportRevision());
	}

	public void testSafeModeDefersPostSyncRefreshUntilProjectIsTrusted() throws Exception {
		discovery();
		awaitLoaded(1);
		TrustedProjects.setProjectTrusted(getProject(), false);
		try {
			provider.imported(2, 1);
			applyLatestImport();
			assertEquals(1, exports.size());
			TrustedProjects.setProjectTrusted(getProject(), true);
			awaitLoaded(2);
			assertEquals(2, getProject().getService(ScenarioCatalog.class).snapshot().getSource().getImportRevision());
		} finally {
			TrustedProjects.setProjectTrusted(getProject(), true);
		}
	}

	public void testFailedImportDoesNotRefreshEvenWhenNativeRevisionHasAdvanced() throws Exception {
		discovery();
		awaitLoaded(1);
		provider.first = 2;
		provider.changed(ProjectChange.IMPORT_FAILED);
		applyLatestImport();
		assertEquals(1, exports.size());
		provider.changed(ProjectChange.IMPORTED);
		awaitLoaded(2);
		assertEquals(2, getProject().getService(ScenarioCatalog.class).snapshot().getSource().getImportRevision());
	}

	public void testCommandsRejectBackgroundThreadsBeforeChangingDiscoveryState() throws Exception {
		SourceDiscovery discovery = getProject().getService(SourceDiscovery.class);
		var before = discovery.snapshot();
		var source = provider.source("first", "First", 1);
		List<Runnable> commands = List.of(discovery::initialize, discovery::refresh,
				discovery::sync, () -> discovery.select(source));

		for (Runnable command : commands) {
			var result = CompletableFuture.runAsync(command);
			try {
				result.get(5, java.util.concurrent.TimeUnit.SECONDS);
				fail("Discovery commands must reject background callers");
			} catch (java.util.concurrent.ExecutionException failure) {
				assertInstanceOf(failure.getCause(), IllegalStateException.class);
				assertEquals("Scenario discovery commands must run on the IDE event thread.", failure.getCause().getMessage());
			}
			assertSame(before, discovery.snapshot());
		}
		assertTrue(provider.prepared.isEmpty());
	}

	public void testBackgroundReadersCanReadPublishedSnapshots() throws Exception {
		SourceDiscovery discovery = discovery();
		awaitLoaded(1);
		var expected = discovery.snapshot();
		var observed = CompletableFuture.supplyAsync(discovery::snapshot)
				.get(5, java.util.concurrent.TimeUnit.SECONDS);

		assertSame(expected, observed);
		assertEquals(DiscoveryState.IDLE, observed.getState());
		assertEquals("fixture:first", observed.getSelectedSource().getId());
	}

	private SourceDiscovery discovery() {
		SourceDiscovery discovery = getProject().getService(SourceDiscovery.class);
		discovery.initialize();
		return discovery;
	}

	private RetainedEnvironmentSession startRun() throws Exception {
		var run = session.start(getProject().getService(ScenarioCatalog.class).snapshot().getSource(), getProject().getService(ScenarioCatalog.class).snapshot().getScenarios().getFirst());
		await(() -> run.getSnapshot().getState() == SessionState.RUNNING);
		return run;
	}

	private void awaitLoaded(int count) throws Exception {
		try {
			await(
					() -> exports.size() == count && getProject().getService(ScenarioCatalog.class).snapshot().getState() == CatalogState.READY);
		} catch (AssertionError failure) {
			throw new AssertionError(
					"Expected "
							+ count
							+ " catalog loads; exports="
							+ exports.size()
							+ ", preparations="
							+ provider.prepared.size()
							+ ", catalogState="
							+ getProject().getService(ScenarioCatalog.class).snapshot().getState()
							+ ", view="
							+ getProject().getService(SourceDiscovery.class).snapshot().toString(),
					failure);
		}
	}

	private void applyLatestImport() throws Exception {
		await(() -> {
			var state = getProject().getService(SourceDiscovery.class).snapshot();
			return state.getState() == DiscoveryState.IDLE
					&& state.getListing().getSources().equals(provider.discover(getProject()).getSources());
		});
	}


	public void testRefreshContinuesAfterTheLastSubscriberCloses() throws Exception {
		SourceDiscovery discovery = discovery();
		var subscriber = Disposer.newDisposable();
		var changes = new AtomicInteger();
		discovery.subscribe(changes::incrementAndGet, subscriber);
		awaitLoaded(1);
		Disposer.dispose(subscriber);
		int previous = changes.get();

		provider.imported(2, 1);
		awaitLoaded(2);
		assertEquals(previous, changes.get());
		assertEquals(2, getProject().getService(ScenarioCatalog.class).snapshot().getSource().getImportRevision());
	}

	private void await(java.util.function.BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
			Thread.sleep(5);
		}
		assertTrue("Discovery condition was not reached", condition.getAsBoolean());
	}

	private static final class CatalogProvider implements BuildIntegration {
		private final Path directory;
		private final List<Consumer<ProjectChange>> listeners = new CopyOnWriteArrayList<>();
		private final List<ScenarioSource> prepared = new CopyOnWriteArrayList<>();
		private final AtomicInteger reads = new AtomicInteger();
		private final AtomicInteger syncs = new AtomicInteger();
		private volatile long first = 1;
		private volatile long second = 1;
		private boolean notifyDuringPreparation;

		private CatalogProvider(Path directory) {
			this.directory = directory;
		}

		@Override
		public @NotNull String getId() {
			return "fixture";
		}

		@Override
		public @NotNull SourceListing discover(@NotNull Project project) {
			reads.incrementAndGet();
			return SourceListing.builder()
					.status(SourceListingStatus.READY)
					.message("Ready")
					.sources(List.of(source("first", "First", first), source("second", "Second", second)))
					.build();
		}

		private ScenarioSource source(String id, String name, long revision) {
			return ScenarioSource.builder()
					.id("fixture:" + id)
					.integrationId("fixture")
					.displayName(name)
					.directory(directory)
					.importRevision(revision)
					.build();
		}

		@Override
		public boolean canSync(@NotNull Project project) {
			return false;
		}

		@Override
		public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
			syncs.incrementAndGet();
			return CompletableFuture.failedFuture(
					new AssertionError("Catalog refresh cannot sync the project"));
		}

		@Override
		public void subscribe(
				@NotNull Project project,
				@NotNull Consumer<ProjectChange> listener,
				@NotNull Disposable owner) {
			listeners.add(listener);
			Disposer.register(owner, () -> listeners.remove(listener));
		}

		@Override
		public @NotNull ScenarioPreparation prepare(
				@NotNull Project project, @NotNull ScenarioSource selected) throws IOException {
			prepared.add(selected);
			Path manifest = Files.createTempFile(directory, "anvil-refresh-", ".json");
			Files.writeString(
					manifest,
					"{\"schemaVersion\":1,\"toolingJavaExecutable\":\"fixture-java\",\"classpath\":[\"fixture-runtime\"],\"definitions\":[\"fixture.Definition\"]}");
			if (notifyDuringPreparation) changed(ProjectChange.IMPORTED);
			return ScenarioPreparation.builder()
					.command(List.of("fixture-prepare"))
					.workingDirectory(directory)
					.manifestPath(manifest)
					.build();
		}

		private void imported(long first, long second) {
			this.first = first;
			this.second = second;
			changed(ProjectChange.IMPORTED);
		}

		private void changed(ProjectChange change) {
			listeners.forEach(listener -> listener.accept(change));
		}
	}
}
