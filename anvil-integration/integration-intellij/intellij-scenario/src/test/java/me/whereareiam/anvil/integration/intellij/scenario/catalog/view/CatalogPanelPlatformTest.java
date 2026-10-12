package me.whereareiam.anvil.integration.intellij.scenario.catalog.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.execution.RunManager;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.UIUtil;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import me.whereareiam.anvil.integration.intellij.ScenarioFixtures;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.ScenarioDataKeys;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.view.tree.ScenarioTree;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.AnvilRunConfiguration;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ControlledProject;
import me.whereareiam.anvil.integration.intellij.scenario.window.component.details.DefinitionDetailsPanel;
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusBadge;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class CatalogPanelPlatformTest extends ScenarioPlatformTestCase {
	private ControlledProject project;
	private final List<Notification> notifications = new CopyOnWriteArrayList<>();

	@Override
	protected boolean isIconRequired() {
		return true;
	}

	@Override
	protected TempDirTestFixture createTempDirTestFixture() {
		return new TempDirTestFixtureImpl();
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		project = ControlledProject.install(getTestRootDisposable(), this::tooling, "main");
		project.setScenariosJson(json(ScenarioFixtures.scenario()));
		getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
			@Override
			public void notify(Notification notification) {
				notifications.add(notification);
			}
		});
	}

	public void testTreeShowsServersAndProxiesBeforeAnythingRuns() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		var root = (DefaultMutableTreeNode) tree.getModel().getRoot();

		assertEquals(1, root.getChildCount());
		assertEquals(2, ((DefaultMutableTreeNode) root.getChildAt(0)).getChildCount());
		assertTrue(project.getRunners().size() == 1 && project.startRequests().isEmpty());
	}

	public void testSelectionIsOfferedToActionsThroughTheDataContext() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		tree.expandRow(0);
		tree.setSelectionRow(1);

		var context = DataManager.getInstance().getDataContext(tree);
		assertEquals(project.source("main"), context.getData(ScenarioSource.DATA_KEY));
		assertEquals(ScenarioFixtures.scenario().getDefinition(), context.getData(ScenarioDataKeys.SCENARIO).getDefinition());
		assertEquals("paper", context.getData(ScenarioDataKeys.PROCESS));
		assertNotNull("Jump to Source and double-click open the definition", context.getData(CommonDataKeys.NAVIGATABLE));

		tree.setSelectionRow(0);
		assertNull(DataManager.getInstance().getDataContext(tree).getData(ScenarioDataKeys.PROCESS));
	}

	public void testRunStartsTheScenarioWithoutSavingAConfiguration() throws Exception {
		var panel = panel();
		int before = RunManager.getInstance(getProject()).getAllSettings().size();

		SwingTestSupport.performAction(panel, "Run Scenario");

		ControlledProject.await(() -> project.startRequests().size() == 1);
		assertTrue(project.startRequests().getFirst().path("target").isMissingNode());
		assertEquals(before, RunManager.getInstance(getProject()).getAllSettings().size());
		EnvironmentLifecycle.getInstance(getProject()).getActiveSession().stop();
	}

	public void testSelectedProcessStartsOnlyThatProcessWithARoleLabel() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		tree.expandRow(0);
		tree.setSelectionRow(2);

		SwingTestSupport.performAction(panel, "Start Proxy");

		ControlledProject.await(() -> project.startRequests().size() == 1);
		assertEquals("proxy", project.startRequests().getFirst().path("target").asText());
		EnvironmentLifecycle.getInstance(getProject()).getActiveSession().stop();
	}

	public void testEnterRunsTheSelectedScenario() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		tree.setSelectionRow(0);

		tree.getActionMap().get("anvil.run").actionPerformed(null);

		ControlledProject.await(() -> project.startRequests().size() == 1);
		EnvironmentLifecycle.getInstance(getProject()).getActiveSession().stop();
	}

	public void testAnotherRunningScenarioDisablesRunAndSaysWhy() throws Exception {
		var panel = panel();
		var running = EnvironmentLifecycle.getInstance(getProject()).run(project.source("main"),
				ScenarioDescriptor.builder().definition("example.Other").name("other").displayName("Other").build(), null);
		UIUtil.dispatchAllInvocationEvents();

		assertFalse(SwingTestSupport.actionEnabled(panel, "Run Scenario"));
		assertEquals("Stop “Other” to run another scenario", SwingTestSupport.actionDescription(panel, "Run Scenario"));
		assertTrue("Stop is offered for the running environment", SwingTestSupport.actionEnabled(panel, "Stop “Other”"));

		SwingTestSupport.performAction(panel, "Stop “Other”");
		ControlledProject.await(() -> !running.isActive());
	}

	public void testSavingSelectsTheConfigurationAndConfirmsWithANotification() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		var manager = RunManager.getInstance(getProject());

		SwingTestSupport.performAction(panel, "Save as Run Configuration");
		var whole = (AnvilRunConfiguration) manager.getSelectedConfiguration().getConfiguration();
		assertEquals("", whole.getProcessId());
		assertTrue(notifications.getLast().getContent().contains("Player registration"));

		tree.expandRow(0);
		tree.setSelectionRow(1);
		SwingTestSupport.performAction(panel, "Save Server as Run Configuration");
		var process = (AnvilRunConfiguration) manager.getSelectedConfiguration().getConfiguration();
		assertNotSame(whole, process);
		assertEquals("paper", process.getProcessId());
		assertNull("Saving never replaces the catalog with a message", ((java.awt.BorderLayout) panel.getLayout())
				.getLayoutComponent(java.awt.BorderLayout.SOUTH));
	}

	public void testExecutionOverlayIsScopedToTheSelectedSourceAndClearsAfterCompletion() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		var source = project.source("main");
		var scenario = ScenarioFixtures.scenario();
		var run = EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		panel.controller.observe(run);
		assertTrue(scenarioTooltip(tree).contains("Starting"));

		var otherSource = source.toBuilder().id(source.getId() + ":other").displayName("Other source").build();
		var other = EnvironmentSessionFixture.create(getProject(), otherSource, scenario, getTestRootDisposable());
		panel.controller.observe(other);
		assertFalse(scenarioTooltip(tree).contains("Starting"));

		EnvironmentSessionFixture.update(run, runningSnapshot());
		panel.controller.observe(run);
		assertTrue(scenarioTooltip(tree).contains("Partially running"));
		EnvironmentSessionFixture.finish(run, 0);
		panel.controller.observe(run);
		assertFalse(scenarioTooltip(tree).contains("Partially running"));
	}

	public void testInspectorShowsTheRunningDeclarationAndLiveState() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		var catalog = ScenarioFixtures.scenario();
		var current = catalog.toBuilder().clearProcesses()
				.process(catalog.getProcesses().getFirst().toBuilder().displayName("Current backend").memoryMegabytes(2048).build())
				.process(catalog.getProcesses().getLast())
				.build();
		var run = EnvironmentSessionFixture.create(getProject(), project.source("main"), current, getTestRootDisposable());
		panel.controller.observe(run);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		EnvironmentSessionFixture.update(run, runningSnapshot());
		var details = SwingTestSupport.find(panel, DefinitionDetailsPanel.class);
		SwingTestSupport.await(() -> "Running".equals(SwingTestSupport.find(details, StatusBadge.class).getText()));

		String text = SwingTestSupport.text(details);
		assertTrue(text.contains("Current backend"));
		assertTrue(text.contains("2048 MiB"));
		assertTrue(text.contains("127.0.0.1:25566"));
		assertNotNull(link(details, "paper"));

		tree.setSelectionRow(2);
		assertEquals("Not started", SwingTestSupport.find(details, StatusBadge.class).getText());
		tree.setSelectionRow(0);
		assertEquals("Partially running", SwingTestSupport.find(details, StatusBadge.class).getText());
		assertTrue(SwingTestSupport.text(details).contains("1 of 2 running"));
	}

	public void testInspectorFollowsUpdatesWithoutRebuildingUnchangedText() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		var run = EnvironmentSessionFixture.create(getProject(), project.source("main"), ScenarioFixtures.scenario(),
				getTestRootDisposable());
		panel.controller.observe(run);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		EnvironmentSessionFixture.update(run, runningSnapshot());
		var details = SwingTestSupport.find(panel, DefinitionDetailsPanel.class);
		SwingTestSupport.await(() -> "Running".equals(SwingTestSupport.find(details, StatusBadge.class).getText()));
		JTextArea distribution = textValue(details, "Pinned Paper distribution");
		distribution.select(0, 6);

		EnvironmentSessionFixture.update(run, run.getSnapshot());
		UIUtil.dispatchAllInvocationEvents();
		assertSame(distribution, textValue(details, "Pinned Paper distribution"));
		assertEquals("Pinned", distribution.getSelectedText());

		var stopped = run.getSnapshot().getProcesses().getFirst().toBuilder().state(ProcessState.STOPPED).build();
		EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().processes(List.of(stopped)).build());
		SwingTestSupport.await(() -> "Stopped".equals(SwingTestSupport.find(details, StatusBadge.class).getText()));
		assertEquals("paper", tree.selectedProcess().getName());

		EnvironmentSessionFixture.finish(run, 1);
		SwingTestSupport.await(() -> "Not started".equals(SwingTestSupport.find(details, StatusBadge.class).getText()));
		assertFalse(SwingTestSupport.text(details).contains("Workspace"));
	}

	public void testInspectorIgnoresAnotherSourceOrDefinition() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		var source = project.source("main");
		var scenario = ScenarioFixtures.scenario();
		var details = SwingTestSupport.find(panel, DefinitionDetailsPanel.class);

		var other = EnvironmentSessionFixture.create(getProject(), source.toBuilder().id("fixture:other").build(), scenario,
				getTestRootDisposable());
		EnvironmentSessionFixture.update(other, runningSnapshot());
		panel.controller.observe(other);
		assertEquals("Not started", SwingTestSupport.find(details, StatusBadge.class).getText());

		var foreign = EnvironmentSessionFixture.create(getProject(), source,
				scenario.toBuilder().definition("other.AuthenticationScenarios").build(), getTestRootDisposable());
		EnvironmentSessionFixture.update(foreign, runningSnapshot());
		panel.controller.observe(foreign);
		assertEquals("Not started", SwingTestSupport.find(details, StatusBadge.class).getText());

		var own = EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		EnvironmentSessionFixture.update(own, runningSnapshot());
		panel.controller.observe(own);
		assertEquals("Running", SwingTestSupport.find(details, StatusBadge.class).getText());
	}

	public void testWorkspaceLinkReportsAWorkspaceThatWasCleanedUp() throws Exception {
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		var run = EnvironmentSessionFixture.create(getProject(), project.source("main"), ScenarioFixtures.scenario(),
				getTestRootDisposable());
		String missing = myFixture.getTempDirFixture().findOrCreateDir("workspaces").toNioPath()
				.resolve("missing-anvil-workspace").toString();
		var live = runningSnapshot();
		EnvironmentSessionFixture.update(run, live.toBuilder()
				.processes(List.of(live.getProcesses().getFirst().toBuilder().workDirectory(missing).build())).build());
		panel.controller.observe(run);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		var details = SwingTestSupport.find(panel, DefinitionDetailsPanel.class);

		link(details, "missing-anvil-workspace").doClick();

		SwingTestSupport.await(() -> SwingTestSupport.text(details).contains("Workspace is no longer available."));
	}

	public void testDisposingTheCatalogKeepsItsEnvironmentRunning() throws Exception {
		var panel = panel();
		var run = EnvironmentSessionFixture.create(getProject(), project.source("main"), ScenarioFixtures.scenario(),
				getTestRootDisposable());
		panel.controller.observe(run);
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		String tooltip = scenarioTooltip(tree);

		Disposer.dispose(panel);
		EnvironmentSessionFixture.update(run, runningSnapshot());
		UIUtil.dispatchAllInvocationEvents();

		assertTrue(run.isActive());
		assertEquals(tooltip, scenarioTooltip(tree));
	}

	public void testReplacingTheSessionIgnoresTheOlderSessionsCompletion() throws Exception {
		var panel = panel();
		var source = project.source("main");
		var scenario = ScenarioFixtures.scenario();
		var first = EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		var second = EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		panel.controller.observe(first);
		EnvironmentSessionFixture.finish(first, 0);
		EnvironmentSessionFixture.update(second, runningSnapshot());
		panel.controller.observe(second);
		UIUtil.dispatchAllInvocationEvents();

		assertTrue(scenarioTooltip(SwingTestSupport.find(panel, ScenarioTree.class)).contains("Partially running"));
		assertTrue(SwingTestSupport.text(panel.details).contains("Partially running"));
	}

	public void testCatalogRendersInDarkAndLightThemes() throws Exception {
		SwingTestSupport.useDarcula(getTestRootDisposable());
		var panel = panel();
		SwingTestSupport.capture(panel, "anvil-scenarios-1200x400.png", 1200, 400);
		SwingTestSupport.capture(panel, "anvil-scenarios-1000x560.png", 1000, 560);
		SwingTestSupport.useTheme(getTestRootDisposable(), false);
		SwingTestSupport.capture(panel, "anvil-scenarios-light-1200x400.png", 1200, 400);
		assertEquals(panel.getBackground(), SwingTestSupport.find(panel, ScenarioTree.class).getBackground());
	}

	public void testGroupedProcessesAreNotRepeatedAsStandaloneEntries() throws Exception {
		var network = ScenarioFixtures.scenario();
		var server = network.getProcesses().getFirst();
		var standalone = ScenarioDescriptor.builder().definition("example.PaperOnly").name("paper").displayName("Paper 1.21.11")
				.entrypoint(server.getName()).process(server).build();
		project.setScenariosJson(json(standalone, network));
		var panel = panel();
		var tree = SwingTestSupport.find(panel, ScenarioTree.class);
		var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
		assertEquals(2, root.getChildCount());

		DefaultMutableTreeNode grouped = null;
		for (int index = 0; index < root.getChildCount(); index++) {
			var candidate = (DefaultMutableTreeNode) root.getChildAt(index);
			if (((ScenarioDescriptor) candidate.getUserObject()).getDefinition().equals(network.getDefinition())) grouped = candidate;
		}
		assertNotNull(grouped);
		assertEquals(2, grouped.getChildCount());
		tree.setSelectionPath(new TreePath(((DefaultMutableTreeNode) grouped.getFirstChild()).getPath()));
		assertEquals(server.getName(), tree.selectedProcess().getName());
	}

	private CatalogPanel panel() throws Exception {
		var panel = new CatalogPanel(getProject());
		Disposer.register(getTestRootDisposable(), panel);
		SwingTestSupport.await(() -> ((DefaultMutableTreeNode) SwingTestSupport.find(panel, ScenarioTree.class).getModel().getRoot())
				.getChildCount() > 0);
		return panel;
	}

	private static String json(ScenarioDescriptor... scenarios) throws Exception {
		return new ObjectMapper().writeValueAsString(List.of(scenarios));
	}

	private static SessionSnapshot runningSnapshot() {
		return SessionSnapshot.builder()
				.state(SessionState.RUNNING)
				.entrypoint("proxy")
				.processes(List.of(ProcessSnapshot.builder()
						.name("paper")
						.executionId(EnvironmentSessionFixture.executionId("paper"))
						.displayName("Paper backend")
						.state(ProcessState.READY)
						.host("127.0.0.1")
						.port(25566)
						.workDirectory("/workspace/paper")
						.build()))
				.build();
	}

	private static LinkLabel<?> link(Container container, String text) {
		for (Component component : container.getComponents()) {
			if (component instanceof LinkLabel<?> label && text.equals(label.getText())) return label;
			if (component instanceof Container child) {
				LinkLabel<?> found = link(child, text);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static JTextArea textValue(Container container, String text) {
		for (Component component : container.getComponents()) {
			if (component instanceof JTextArea value && text.equals(value.getText())) return value;
			if (component instanceof Container child) {
				JTextArea found = textValue(child, text);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static String scenarioTooltip(ScenarioTree tree) {
		Object node = ((DefaultMutableTreeNode) tree.getModel().getRoot()).getFirstChild();
		var renderer = (JComponent) tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, true, false, 0, false);
		return renderer.getToolTipText();
	}
}
