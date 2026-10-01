package me.whereareiam.anvil.integration.intellij.view.window.main;

import com.intellij.execution.RunManager;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.ProjectEnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.EnvironmentSessionPanel;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.EnvironmentViewState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import org.jetbrains.annotations.NotNull;

public class MainWindowControllerPlatformTest extends UiPlatformTestCase {
	private ProjectEnvironmentLifecycle sessions;
	private ControlledProvider provider;
	private ContentManager contents;
	private MainWindowController coordinator;
	private PersistentPreferences.PreferenceState savedSettings;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		savedSettings = PersistentPreferences.getInstance().getState();
		PersistentPreferences.getInstance().loadState(new PersistentPreferences.PreferenceState());
		getProject().getService(EnvironmentViewState.class).loadState(new EnvironmentViewState.Options());
		sessions = tooling(ProcessBuilder::start);
		provider = new ControlledProvider();
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(provider), getTestRootDisposable());
		contents = ContentFactory.getInstance().createContentManager(true, getProject());
		Disposer.register(getTestRootDisposable(), contents);
		coordinator = new MainWindowController(getProject(), contents);
		WindowTestSupport.await(
				() ->
						WindowTestSupport.text(contents.getContent(0).getComponent())
								.contains("Set up Anvil"));
		provider.available = true;
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			provider.gate.release.countDown();
			// Closing tabs of still-active runs asks for confirmation; teardown confirms each one.
			TestDialogManager.setTestDialog(TestDialog.OK);
			contents.removeAllContents(true);
			WindowTestSupport.await(() -> !sessions.hasActiveSession());
		} finally {
			TestDialogManager.setTestDialog(TestDialog.DEFAULT);
			PersistentPreferences.getInstance().loadState(savedSettings);
			super.tearDown();
		}
	}

	public void testDefaultRunCreatesOwnedTabWithoutRunConfiguration() throws Exception {
		int before = RunManager.getInstance(getProject()).getAllSettings().size();
		var run = coordinator.start(WindowTestSupport.source(), WindowTestSupport.scenario());
		WindowTestSupport.await(() -> provider.gate.started.getCount() == 0);
		assertEquals(before, RunManager.getInstance(getProject()).getAllSettings().size());
		assertEquals(2, contents.getContentCount());
		assertEquals("Scenarios", contents.getContent(0).getDisplayName());
		assertFalse(contents.getContent(0).isCloseable());
		assertTrue(contents.getContent(1).isCloseable());
		assertTrue(contents.getContent(1).getDisplayName().startsWith("Player registration"));
		assertTrue(run.isActive());
		TestDialogManager.setTestDialog(TestDialog.OK);
		try {
			contents.removeContent(contents.getContent(1), true);
		} finally {
			TestDialogManager.setTestDialog(TestDialog.DEFAULT);
		}
		provider.gate.release.countDown();
		WindowTestSupport.await(() -> !sessions.hasActiveSession());
		assertEquals(1, contents.getContentCount());
		assertTrue(sessions.getSessions().isEmpty());
	}

	public void testDecliningTheCloseConfirmationKeepsAnActiveRunAndItsTab() throws Exception {
		var run = coordinator.start(WindowTestSupport.source(), WindowTestSupport.scenario());
		WindowTestSupport.await(() -> provider.gate.started.getCount() == 0);

		TestDialogManager.setTestDialog(TestDialog.NO);
		try {
			assertFalse(contents.removeContent(contents.getContent(1), true));
		} finally {
			TestDialogManager.setTestDialog(TestDialog.DEFAULT);
		}

		assertEquals(2, contents.getContentCount());
		assertTrue(run.isActive());
		provider.gate.release.countDown();
	}

	public void testClosingCatalogDoesNotStopActiveRun() throws Exception {
		var run = coordinator.start(WindowTestSupport.source(), WindowTestSupport.scenario());
		WindowTestSupport.await(() -> provider.gate.started.getCount() == 0);
		contents.removeContent(contents.getContent(0), true);
		assertTrue(run.isActive());
		assertTrue(sessions.hasActiveSession());
	}

	public void testCompletedTabRemainsAndClosingItDoesNotStopNewerRun() throws Exception {
		provider.gate.release.countDown();
		var first =
				coordinator.start(WindowTestSupport.source(), WindowTestSupport.scenario());
		WindowTestSupport.await(() -> !first.isActive());
		var completed = contents.getContent(1);
		assertEquals(2, contents.getContentCount());
		assertEquals(2, first.getScenario().getProcesses().size());
		assertNotNull(((EnvironmentSessionPanel) completed.getComponent()).consolePresentation().getConsole().getComponent());
		provider.gate = new Gate();
		var second =
				coordinator.start(WindowTestSupport.source(), WindowTestSupport.scenario());
		WindowTestSupport.await(() -> provider.gate.started.getCount() == 0);
		contents.removeContent(completed, true);
		assertTrue(second.isActive());
		assertTrue(sessions.hasActiveSession());
		assertEquals(2, contents.getContentCount());
	}

	public void testFailureRevealsConsoleOnceWithoutChangingLastUsedSection() {
		RetainedEnvironmentSession failed = fixtureRun();
		Content tab = coordinator.addSession(failed);
		EnvironmentSessionPanel panel = (EnvironmentSessionPanel) tab.getComponent();
		panel.tabs().setSelectedIndex(2);
		contents.setSelectedContent(contents.getContent(0), false);
		EnvironmentSessionFixture.append(failed, "paper", "ERROR an ordinary server log line\n", true);

				EnvironmentSessionFixture.update(failed,
				failed.getSnapshot().toBuilder().state(SessionState.RUNNING).build());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals("Scenarios", contents.getSelectedContent().getDisplayName());
		assertEquals(2, panel.tabs().getSelectedIndex());

				EnvironmentSessionFixture.update(failed,
				failed.getSnapshot().toBuilder()
						.state(SessionState.FAILED)
						.failure("Process failed")
						.build());
		UIUtil.dispatchAllInvocationEvents();
		assertSame(tab, contents.getSelectedContent());
		assertEquals(1, panel.tabs().getSelectedIndex());
		assertEquals(
				SessionSection.PLAYERS, getProject().getService(EnvironmentViewState.class).getLastSection());
		panel.tabs().setSelectedIndex(0);
		contents.setSelectedContent(contents.getContent(0), false);
		EnvironmentSessionFixture.update(failed, failed.getSnapshot());
		EnvironmentSessionFixture.finish(failed, 1);
		UIUtil.dispatchAllInvocationEvents();
		assertEquals("Scenarios", contents.getSelectedContent().getDisplayName());
		assertEquals(0, panel.tabs().getSelectedIndex());
	}

	public void testDisabledFailureRevealDoesNotApplyRetroactively() {
		PersistentPreferences.getInstance().setShowConsoleOnFailure(false);
		RetainedEnvironmentSession run = fixtureRun();
		Content tab = coordinator.addSession(run);
		contents.setSelectedContent(contents.getContent(0), false);

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder().state(SessionState.FAILED).failure("Failed").build());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(0, ((EnvironmentSessionPanel) tab.getComponent()).tabs().getSelectedIndex());
		assertEquals("Scenarios", contents.getSelectedContent().getDisplayName());
		PersistentPreferences.getInstance().setShowConsoleOnFailure(true);
		EnvironmentSessionFixture.update(run, run.getSnapshot());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(0, ((EnvironmentSessionPanel) tab.getComponent()).tabs().getSelectedIndex());
		assertEquals("Scenarios", contents.getSelectedContent().getDisplayName());
	}

	public void testAlreadyFailedRunRevealsWhenItsTabIsFirstCreated() {
		RetainedEnvironmentSession run = fixtureRun();

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.state(SessionState.FAILED)
						.failure("Fast preparation failure")
						.build());
		Content tab = coordinator.addSession(run);
		assertSame(tab, contents.getSelectedContent());
		assertEquals(1, ((EnvironmentSessionPanel) tab.getComponent()).tabs().getSelectedIndex());
	}

	public void testDefaultRetentionDisposesOldestSuccessAndPreservesFailedRuns() {
		RetainedEnvironmentSession oldest = fixtureRun();
		Content oldestTab = coordinator.addSession(oldest);
		complete(oldest, SessionState.STOPPED, 0);
		RetainedEnvironmentSession failure = fixtureRun();
		Content failureTab = coordinator.addSession(failure);
		complete(failure, SessionState.FAILED, 1);
		for (int index = 0; index < 5; index++) {
			RetainedEnvironmentSession run = fixtureRun();
			coordinator.addSession(run);
			complete(run, SessionState.STOPPED, 0);
		}
		assertEquals(7, contents.getContentCount());
		assertTrue(removed(oldestTab));
		assertTrue(EnvironmentSessionFixture.disposed(oldest));
		assertFalse(removed(failureTab));
		assertFalse(EnvironmentSessionFixture.disposed(failure));
	}

	public void testRetentionWaitsForCleanupAndReactsToSettingsChanges() {
		PersistentPreferences.getInstance().setCompletedSuccessfulTabs(0);
		RetainedEnvironmentSession first = fixtureRun();
		Content firstTab = coordinator.addSession(first);
		complete(first, SessionState.STOPPED, 0);
		RetainedEnvironmentSession second = fixtureRun();
		Content secondTab = coordinator.addSession(second);
		complete(second, SessionState.STOPPED, 0);
		RetainedEnvironmentSession cleaning = fixtureRun();
		Content cleaningTab = coordinator.addSession(cleaning);

				EnvironmentSessionFixture.update(cleaning,
				cleaning.getSnapshot().toBuilder().state(SessionState.STOPPED).build());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(4, contents.getContentCount());
		PersistentPreferences.getInstance().setCompletedSuccessfulTabs(1);
		assertTrue(removed(firstTab));
		assertFalse(removed(secondTab));
		assertFalse(removed(cleaningTab));
		assertTrue(cleaning.isActive());
		ProcessHandler cleaningHandler = ((EnvironmentSessionPanel) cleaningTab.getComponent()).consolePresentation().getProcessHandler();
		cleaningHandler
				.addProcessListener(
						new ProcessAdapter() {
							@Override
							public void processWillTerminate(
									@NotNull ProcessEvent event, boolean willBeDestroyed) {
								assertFalse(cleaning.isActive());
								assertFalse(cleaningHandler.isProcessTerminated());
								PersistentPreferences.getInstance().setCompletedSuccessfulTabs(2);
								PersistentPreferences.getInstance().setCompletedSuccessfulTabs(1);
								assertFalse(removed(secondTab));
							}
						});
		complete(cleaning, SessionState.STOPPED, 0);
		assertTrue(removed(secondTab));
		assertFalse(removed(cleaningTab));
		assertEquals(2, contents.getContentCount());
	}

	public void testNonzeroExitAndFailureDiagnosticAreNeverPrunedAsSuccess() {
		PersistentPreferences.getInstance().setCompletedSuccessfulTabs(1);
		RetainedEnvironmentSession nonzero = fixtureRun();
		Content nonzeroTab = coordinator.addSession(nonzero);
		complete(nonzero, SessionState.STOPPED, 1);
		RetainedEnvironmentSession diagnostic = fixtureRun();
		Content diagnosticTab = coordinator.addSession(diagnostic);

				EnvironmentSessionFixture.update(diagnostic,
				diagnostic.getSnapshot().toBuilder().failure("Cleanup incomplete").build());
		complete(diagnostic, SessionState.STOPPED, 0);
		for (int index = 0; index < 2; index++) {
			RetainedEnvironmentSession run = fixtureRun();
			coordinator.addSession(run);
			complete(run, SessionState.STOPPED, 0);
		}
		assertFalse(removed(nonzeroTab));
		assertFalse(removed(diagnosticTab));
		assertEquals(4, contents.getContentCount());
	}

	public void testPreviouslyFailedRunRemainsEvenIfLaterSnapshotClearsTheDiagnostic() {
		PersistentPreferences.getInstance().setCompletedSuccessfulTabs(1);
		RetainedEnvironmentSession failed = fixtureRun();
		Content failedTab = coordinator.addSession(failed);

				EnvironmentSessionFixture.update(failed,
				failed.getSnapshot().toBuilder()
						.state(SessionState.FAILED)
						.failure("Original failure")
						.build());
		UIUtil.dispatchAllInvocationEvents();

				EnvironmentSessionFixture.update(failed,
				failed.getSnapshot().toBuilder().state(SessionState.STOPPED).failure(null).build());
		EnvironmentSessionFixture.finish(failed, 0);
		UIUtil.dispatchAllInvocationEvents();
		RetainedEnvironmentSession success = fixtureRun();
		coordinator.addSession(success);
		complete(success, SessionState.STOPPED, 0);
		assertFalse(removed(failedTab));
		assertEquals(3, contents.getContentCount());
	}

	private RetainedEnvironmentSession fixtureRun() {
		return EnvironmentSessionFixture.create(
				getProject(),
				WindowTestSupport.source(),
				WindowTestSupport.scenario(),
				getTestRootDisposable());
	}

	private void complete(RetainedEnvironmentSession run, SessionState state, int exitCode) {

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().state(state).build());
		EnvironmentSessionFixture.finish(run, exitCode);
		UIUtil.dispatchAllInvocationEvents();
	}

	private static final class Gate {
		private final CountDownLatch started = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
	}

	private static final class ControlledProvider implements BuildIntegration {
		private volatile boolean available;
		private volatile Gate gate = new Gate();

		@Override
		public @NotNull String getId() {
			return "fixture-window";
		}

		@Override
		public @NotNull SourceListing discover(@NotNull Project project) {
			return SourceListing.builder()
					.sources(available ? List.of(WindowTestSupport.source()) : List.of())
					.status(available ? SourceListingStatus.READY : SourceListingStatus.UNSUPPORTED)
					.message("Fixture")
					.build();
		}

		@Override
		public boolean canSync(@NotNull Project project) {
			return false;
		}

		@Override
		public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
			return CompletableFuture.failedFuture(new UnsupportedOperationException());
		}

		@Override
		public void subscribe(
				@NotNull Project project,
				@NotNull Consumer<ProjectChange> listener,
				@NotNull Disposable owner) {}

		@Override
		public @NotNull ScenarioPreparation prepare(
				@NotNull Project project, @NotNull ScenarioSource selected) throws IOException {
			Gate current = gate;
			current.started.countDown();
			try {
				if (!current.release.await(5, TimeUnit.SECONDS))
					throw new IOException("Fixture preparation timed out");
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				throw new IOException(failure);
			}
			throw new IOException("Controlled preparation completed without starting a process");
		}
	}

	private boolean removed(Content tab) {
		return contents.getIndexOfContent(tab) < 0;
	}
}
