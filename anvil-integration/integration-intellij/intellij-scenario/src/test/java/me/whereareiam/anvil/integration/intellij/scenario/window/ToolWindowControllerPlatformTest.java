package me.whereareiam.anvil.integration.intellij.scenario.window;

import com.intellij.execution.RunManager;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl;
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

import me.whereareiam.anvil.integration.intellij.ScenarioFixtures;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.EnvironmentTab;
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.SectionMemory;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;

public class ToolWindowControllerPlatformTest extends ScenarioPlatformTestCase {
	private EnvironmentLifecycle sessions;
	private ControlledProvider provider;
	private ContentManager contents;
	private ToolWindowController coordinator;
	private ScenarioSettings.Options savedSettings;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		savedSettings = ScenarioSettings.getInstance().getState();
		ScenarioSettings.getInstance().loadState(new ScenarioSettings.Options());
		getProject().getService(SectionMemory.class).loadState(new SectionMemory.Options());
		sessions = tooling(ProcessBuilder::start);
		provider = new ControlledProvider();
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(provider), getTestRootDisposable());
		contents = ContentFactory.getInstance().createContentManager(true, getProject());
		Disposer.register(getTestRootDisposable(), contents);
		coordinator = new ToolWindowController(getProject(), new ToolWindowHeadlessManagerImpl.MockToolWindow(getProject()), contents);
		SwingTestSupport.await(
				() ->
						SwingTestSupport.text(contents.getContent(0).getComponent())
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
			SwingTestSupport.await(() -> !sessions.hasActiveSession());
		} finally {
			TestDialogManager.setTestDialog(TestDialog.DEFAULT);
			ScenarioSettings.getInstance().loadState(savedSettings);
			super.tearDown();
		}
	}

	public void testDefaultRunCreatesOwnedTabWithoutRunConfiguration() throws Exception {
		int before = RunManager.getInstance(getProject()).getAllSettings().size();
		var run = start(ScenarioFixtures.source(), ScenarioFixtures.scenario());
		SwingTestSupport.await(() -> provider.gate.started.getCount() == 0);
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
		SwingTestSupport.await(() -> !sessions.hasActiveSession());
		assertEquals(1, contents.getContentCount());
		assertTrue(sessions.getSessions().isEmpty());
	}

	public void testDecliningTheCloseConfirmationKeepsAnActiveRunAndItsTab() throws Exception {
		var run = start(ScenarioFixtures.source(), ScenarioFixtures.scenario());
		SwingTestSupport.await(() -> provider.gate.started.getCount() == 0);

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
		var run = start(ScenarioFixtures.source(), ScenarioFixtures.scenario());
		SwingTestSupport.await(() -> provider.gate.started.getCount() == 0);
		contents.removeContent(contents.getContent(0), true);
		assertTrue(run.isActive());
		assertTrue(sessions.hasActiveSession());
	}

	public void testCompletedTabRemainsAndClosingItDoesNotStopNewerRun() throws Exception {
		provider.gate.release.countDown();
		var first =
				start(ScenarioFixtures.source(), ScenarioFixtures.scenario());
		SwingTestSupport.await(() -> !first.isActive());
		var completed = contents.getContent(1);
		assertEquals(2, contents.getContentCount());
		assertEquals(2, first.getScenario().getProcesses().size());
		assertNotNull(((EnvironmentTab) completed.getComponent()).consolePresentation().getConsole().getComponent());
		provider.gate = new Gate();
		var second =
				start(ScenarioFixtures.source(), ScenarioFixtures.scenario());
		SwingTestSupport.await(() -> provider.gate.started.getCount() == 0);
		contents.removeContent(completed, true);
		assertTrue(second.isActive());
		assertTrue(sessions.hasActiveSession());
		assertEquals(2, contents.getContentCount());
	}

	public void testFailureRevealsConsoleOnceWithoutChangingLastUsedSection() {
		EnvironmentSession failed = fixtureRun();
		Content tab = coordinator.addSession(failed);
		EnvironmentTab panel = (EnvironmentTab) tab.getComponent();
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
				ScenarioSettings.Section.PLAYERS, getProject().getService(SectionMemory.class).getLastSection());
		panel.tabs().setSelectedIndex(0);
		contents.setSelectedContent(contents.getContent(0), false);
		EnvironmentSessionFixture.update(failed, failed.getSnapshot());
		EnvironmentSessionFixture.finish(failed, 1);
		UIUtil.dispatchAllInvocationEvents();
		assertEquals("Scenarios", contents.getSelectedContent().getDisplayName());
		assertEquals(0, panel.tabs().getSelectedIndex());
	}

	public void testDisabledFailureRevealDoesNotApplyRetroactively() {
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().revealConsoleOnFailure(false).build());
		EnvironmentSession run = fixtureRun();
		Content tab = coordinator.addSession(run);
		contents.setSelectedContent(contents.getContent(0), false);

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder().state(SessionState.FAILED).failure("Failed").build());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(0, ((EnvironmentTab) tab.getComponent()).tabs().getSelectedIndex());
		assertEquals("Scenarios", contents.getSelectedContent().getDisplayName());
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().revealConsoleOnFailure(true).build());
		EnvironmentSessionFixture.update(run, run.getSnapshot());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(0, ((EnvironmentTab) tab.getComponent()).tabs().getSelectedIndex());
		assertEquals("Scenarios", contents.getSelectedContent().getDisplayName());
	}

	public void testAFailureOutOfViewIsReportedWithALinkToItsConsole() {
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().revealConsoleOnFailure(false).build());
		var notifications = new java.util.concurrent.CopyOnWriteArrayList<com.intellij.notification.Notification>();
		getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(com.intellij.notification.Notifications.TOPIC,
				new com.intellij.notification.Notifications() {
					@Override
					public void notify(com.intellij.notification.Notification notification) {
						notifications.add(notification);
					}
				});
		EnvironmentSession run = fixtureRun();
		coordinator.addSession(run);
		contents.setSelectedContent(contents.getContent(0), false);

		EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().state(SessionState.FAILED).failure("Port 25565 is in use").build());
		UIUtil.dispatchAllInvocationEvents();

		assertEquals(1, notifications.size());
		assertEquals("“Player registration” failed", notifications.getFirst().getTitle());
		assertEquals("Port 25565 is in use", notifications.getFirst().getContent());
		assertEquals("Show Console", notifications.getFirst().getActions().getFirst().getTemplateText());
	}

	public void testAlreadyFailedRunRevealsWhenItsTabIsFirstCreated() {
		EnvironmentSession run = fixtureRun();

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.state(SessionState.FAILED)
						.failure("Fast preparation failure")
						.build());
		Content tab = coordinator.addSession(run);
		assertSame(tab, contents.getSelectedContent());
		assertEquals(1, ((EnvironmentTab) tab.getComponent()).tabs().getSelectedIndex());
	}

	public void testDefaultRetentionDisposesOldestSuccessAndPreservesFailedRuns() {
		EnvironmentSession oldest = fixtureRun();
		Content oldestTab = coordinator.addSession(oldest);
		complete(oldest, SessionState.STOPPED, 0);
		EnvironmentSession failure = fixtureRun();
		Content failureTab = coordinator.addSession(failure);
		complete(failure, SessionState.FAILED, 1);
		for (int index = 0; index < 5; index++) {
			EnvironmentSession run = fixtureRun();
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
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().completedTabs(0).build());
		EnvironmentSession first = fixtureRun();
		Content firstTab = coordinator.addSession(first);
		complete(first, SessionState.STOPPED, 0);
		EnvironmentSession second = fixtureRun();
		Content secondTab = coordinator.addSession(second);
		complete(second, SessionState.STOPPED, 0);
		EnvironmentSession cleaning = fixtureRun();
		Content cleaningTab = coordinator.addSession(cleaning);

				EnvironmentSessionFixture.update(cleaning,
				cleaning.getSnapshot().toBuilder().state(SessionState.STOPPED).build());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(4, contents.getContentCount());
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().completedTabs(1).build());
		assertTrue(removed(firstTab));
		assertFalse(removed(secondTab));
		assertFalse(removed(cleaningTab));
		assertTrue(cleaning.isActive());
		ProcessHandler cleaningHandler = ((EnvironmentTab) cleaningTab.getComponent()).consolePresentation().getProcessHandler();
		cleaningHandler
				.addProcessListener(
						new ProcessAdapter() {
							@Override
							public void processWillTerminate(
									@NotNull ProcessEvent event, boolean willBeDestroyed) {
								assertFalse(cleaning.isActive());
								assertFalse(cleaningHandler.isProcessTerminated());
								ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().completedTabs(2).build());
								ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().completedTabs(1).build());
								assertFalse(removed(secondTab));
							}
						});
		complete(cleaning, SessionState.STOPPED, 0);
		assertTrue(removed(secondTab));
		assertFalse(removed(cleaningTab));
		assertEquals(2, contents.getContentCount());
	}

	public void testNonzeroExitAndFailureDiagnosticAreNeverPrunedAsSuccess() {
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().completedTabs(1).build());
		EnvironmentSession nonzero = fixtureRun();
		Content nonzeroTab = coordinator.addSession(nonzero);
		complete(nonzero, SessionState.STOPPED, 1);
		EnvironmentSession diagnostic = fixtureRun();
		Content diagnosticTab = coordinator.addSession(diagnostic);

				EnvironmentSessionFixture.update(diagnostic,
				diagnostic.getSnapshot().toBuilder().failure("Cleanup incomplete").build());
		complete(diagnostic, SessionState.STOPPED, 0);
		for (int index = 0; index < 2; index++) {
			EnvironmentSession run = fixtureRun();
			coordinator.addSession(run);
			complete(run, SessionState.STOPPED, 0);
		}
		assertFalse(removed(nonzeroTab));
		assertFalse(removed(diagnosticTab));
		assertEquals(4, contents.getContentCount());
	}

	public void testPreviouslyFailedRunRemainsEvenIfLaterSnapshotClearsTheDiagnostic() {
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().completedTabs(1).build());
		EnvironmentSession failed = fixtureRun();
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
		EnvironmentSession success = fixtureRun();
		coordinator.addSession(success);
		complete(success, SessionState.STOPPED, 0);
		assertFalse(removed(failedTab));
		assertEquals(3, contents.getContentCount());
	}

	private EnvironmentSession fixtureRun() {
		return EnvironmentSessionFixture.create(
				getProject(),
				ScenarioFixtures.source(),
				ScenarioFixtures.scenario(),
				getTestRootDisposable());
	}

	private void complete(EnvironmentSession run, SessionState state, int exitCode) {

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
					.sources(available ? List.of(ScenarioFixtures.source()) : List.of())
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

	private EnvironmentSession start(ScenarioSource source, ScenarioDescriptor scenario) throws Exception {
		int tabs = contents.getContentCount();
		var session = sessions.run(source, scenario, null);
		SwingTestSupport.await(() -> contents.getContentCount() > tabs || sessions.getSessions().size() < 1);
		return session;
	}
}
