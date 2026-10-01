package me.whereareiam.anvil.integration.intellij.view.window.main.catalog;

import com.intellij.execution.RunManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.UIUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.model.source.DiscoverySnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.runconfiguration.AnvilRunConfiguration;
import me.whereareiam.anvil.integration.intellij.runconfiguration.RunConfigurationService;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.type.source.DiscoveryState;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation;
import me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree.ScenarioTreeView;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.details.DefinitionDetailsPanel;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusBadge;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class ScenarioCatalogPlatformTest extends UiPlatformTestCase {
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
		ServiceContainerUtil.replaceService(getProject(), SourceDiscovery.class, discovery, getTestRootDisposable());

		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(), getTestRootDisposable());
	}

	public void testScenarioTreeShowsServerAndProxyDefinitionsBeforeRun() throws Exception {
		AtomicInteger starts = new AtomicInteger();
		ScenarioCatalogPanel panel = panel(starts);
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
		assertEquals(1, root.getChildCount());
		assertEquals(2, ((DefaultMutableTreeNode) root.getChildAt(0)).getChildCount());
		tree.expandRow(0);
		tree.setSelectionRow(1);
		assertNotNull(tree.selectedProcess());
		assertEquals(0, starts.get());
		tree.getActionMap().get("anvil.run.scenario").actionPerformed(null);
		assertEquals("Enter on a component must not launch a different lifecycle", 0, starts.get());
		tree.setSelectionRow(0);
		tree.getActionMap().get("anvil.run.scenario").actionPerformed(null);
		assertEquals(1, starts.get());
	}

	public void testRunActionDoesNotSaveConfigurationAndExplicitSaveDoes() throws Exception {
		AtomicInteger starts = new AtomicInteger();
		var panel = panel(starts);
		var scenario = WindowTestSupport.scenario().toBuilder().name("save-idempotency").build();
		panel.applyCatalog(List.of(scenario));
		int before = RunManager.getInstance(getProject()).getAllSettings().size();
		WindowTestSupport.performAction(panel, "Run scenario");
		assertEquals(1, starts.get());
		assertEquals(before, RunManager.getInstance(getProject()).getAllSettings().size());
		WindowTestSupport.performAction(panel, "Save run configuration");
		assertEquals(before + 1, RunManager.getInstance(getProject()).getAllSettings().size());
		getProject().getService(RunConfigurationService.class).save(WindowTestSupport.source(), scenario, null);
		assertEquals(before + 1, RunManager.getInstance(getProject()).getAllSettings().size());
	}

	public void testCatalogHasNoRoutineFooterAndKeepsExplicitActionFeedback() throws Exception {
		var panel = panel(new AtomicInteger());
		assertNull(((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH));
		String text = WindowTestSupport.text(panel);
		assertFalse(text.contains("1 scenario"));
		assertFalse(text.contains("Environment running"));
		WindowTestSupport.performAction(panel, "Save run configuration");
		assertTrue(WindowTestSupport.text(panel).contains("Saved run configuration:"));
		assertNull(((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH));
	}

	public void testCatalogExecutionIsScopedToSelectedModuleAndClearsAfterCompletion()
			throws Exception {
		var panel = panel(new AtomicInteger());
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		var source = WindowTestSupport.source();
		var scenario = WindowTestSupport.scenario();
		var run =
				EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		panel.controller.execution().showExecution(run);
		assertTrue(scenarioTooltip(tree).contains("Starting"));
		var otherModule =
				ScenarioSource.builder()
						.id(source.getId() + ":other")
						.integrationId(source.getIntegrationId())
						.displayName("Other source")
						.directory(source.getDirectory().resolve("other"))
						.build();
		var other =
				EnvironmentSessionFixture.create(getProject(), otherModule, scenario, getTestRootDisposable());
		panel.controller.execution().showExecution(other);
		assertFalse(scenarioTooltip(tree).contains("Starting"));

				EnvironmentSessionFixture.update(run,
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.processes(
								List.of(
										ProcessSnapshot.builder()
												.name("paper")
												.executionId(EnvironmentSessionFixture.executionId("paper"))
												.displayName("Paper backend")
												.state(ProcessState.READY)
												.host("127.0.0.1")
												.port(25566)
												.workDirectory("/workspace/paper")
												.build()))
						.build());
		panel.controller.execution().showExecution(run);
		assertTrue(
				"One running component activates the scenario icon",
				scenarioTooltip(tree).contains("Partially running"));
		EnvironmentSessionFixture.finish(run, 0);
		panel.controller.execution().showExecution(run);
		assertFalse(scenarioTooltip(tree).contains("Partially running"));
		assertFalse(scenarioTooltip(tree).contains("Starting"));
	}

	public void testCatalogInspectorUsesActiveDeclarationAndSnapshotForSelection() throws Exception {
		var panel = panel(new AtomicInteger());
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		var catalog = WindowTestSupport.scenario();
		var current =
				catalog.toBuilder()
						.clearProcesses()
						.process(
								catalog.getProcesses().getFirst().toBuilder()
										.displayName("Current backend")
										.description("The declaration prepared for this execution.")
										.memoryMegabytes(2048)
										.build())
						.process(catalog.getProcesses().getLast())
						.build();
		var run =
				EnvironmentSessionFixture.create(
						getProject(), WindowTestSupport.source(), current, getTestRootDisposable());
		panel.controller.execution().showExecution(run);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		EnvironmentSessionFixture.update(run, runningSnapshot());
		var details = WindowTestSupport.find(panel, DefinitionDetailsPanel.class);
		WindowTestSupport.await(
				() ->
						"Running"
								.equals(WindowTestSupport.find(details, StatusBadge.class).getText()));
		String text = WindowTestSupport.text(details);
		assertTrue(text.contains("Current backend"));
		assertTrue(text.contains("2048 MiB"));
		assertTrue(text.contains("127.0.0.1:25566"));
		assertNotNull(link(details, "paper"));
		assertEquals(
				current.getDefinition(), link(details, "AuthenticationScenarios").getToolTipText());

		tree.setSelectionRow(2);
		assertEquals(
				"Not started", WindowTestSupport.find(details, StatusBadge.class).getText());
		assertFalse(WindowTestSupport.text(details).contains("Address"));
		assertFalse(WindowTestSupport.text(details).contains("Workspace"));
		tree.setSelectionRow(0);
		assertEquals(
				"Partially running",
				WindowTestSupport.find(details, StatusBadge.class).getText());
		assertTrue(WindowTestSupport.text(details).contains("1 of 2 running"));
	}

	public void testInspectorTracksRunUpdatesWithoutReselectingAndPreservesUnchangedTextSelection()
			throws Exception {
		var panel = panel(new AtomicInteger());
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		var run =
				EnvironmentSessionFixture.create(
						getProject(),
						WindowTestSupport.source(),
						WindowTestSupport.scenario(),
						getTestRootDisposable());
		panel.controller.execution().showExecution(run);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		EnvironmentSessionFixture.update(run, runningSnapshot());
		var details = WindowTestSupport.find(panel, DefinitionDetailsPanel.class);
		WindowTestSupport.await(
				() ->
						"Running"
								.equals(WindowTestSupport.find(details, StatusBadge.class).getText()));
		JTextArea distribution = textValue(details, "Pinned Paper distribution");
		distribution.select(0, 6);
		EnvironmentSessionFixture.update(run, run.getSnapshot());
		UIUtil.dispatchAllInvocationEvents();
		assertSame(distribution, textValue(details, "Pinned Paper distribution"));
		assertEquals("Pinned", distribution.getSelectedText());
		var stopped = run.getSnapshot().getProcesses().getFirst().toBuilder().state(ProcessState.STOPPED).build();

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().processes(List.of(stopped)).build());
		WindowTestSupport.await(
				() ->
						"Stopped"
								.equals(WindowTestSupport.find(details, StatusBadge.class).getText()));
		assertEquals("paper", tree.selectedProcess().getName());

				EnvironmentSessionFixture.update(run, runningSnapshot().toBuilder().state(SessionState.FAILED).build());
		WindowTestSupport.await(
				() ->
						"Status unavailable"
								.equals(WindowTestSupport.find(details, StatusBadge.class).getText()));
		EnvironmentSessionFixture.finish(run, 1);
		WindowTestSupport.await(
				() ->
						"Not started"
								.equals(WindowTestSupport.find(details, StatusBadge.class).getText()));
		assertFalse(WindowTestSupport.text(details).contains("Workspace"));
		assertFalse(WindowTestSupport.text(details).contains("Address"));
	}

	public void testInspectorNeverUsesAnotherModuleOrProviderWithSameScenarioName() throws Exception {
		var panel = panel(new AtomicInteger());
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		var source = WindowTestSupport.source();
		var otherModule =
				ScenarioSource.builder()
						.id(source.getId() + ":other")
						.integrationId(source.getIntegrationId())
						.displayName("Other source")
						.directory(source.getDirectory().resolve("other"))
						.build();
		var scenario = WindowTestSupport.scenario();
		var other =
				EnvironmentSessionFixture.create(getProject(), otherModule, scenario, getTestRootDisposable());
		EnvironmentSessionFixture.update(other, runningSnapshot());
		panel.controller.execution().showExecution(other);
		var details = WindowTestSupport.find(panel, DefinitionDetailsPanel.class);
		assertEquals(
				"Not started", WindowTestSupport.find(details, StatusBadge.class).getText());
		var foreign =
				EnvironmentSessionFixture.create(
						getProject(),
						source,
						scenario.toBuilder().definition("other.AuthenticationScenarios").build(),
						getTestRootDisposable());
		EnvironmentSessionFixture.update(foreign, runningSnapshot());
		panel.controller.execution().showExecution(foreign);
		assertEquals(
				"Not started", WindowTestSupport.find(details, StatusBadge.class).getText());
		assertFalse(WindowTestSupport.text(details).contains("Workspace"));
		assertEquals(
				scenario.getDefinition(), link(details, "AuthenticationScenarios").getToolTipText());

		var own =
				EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		EnvironmentSessionFixture.update(own, runningSnapshot());
		panel.controller.execution().showExecution(own);
		assertEquals("Running", WindowTestSupport.find(details, StatusBadge.class).getText());
		showDiscovery(
				SourceListing.builder()
						.sources(List.of(otherModule))
						.status(SourceListingStatus.READY)
						.message("Ready")
						.build(),
				false);
		panel.applyCatalog(List.of(scenario));
		tree.expandRow(0);
		tree.setSelectionRow(1);

				EnvironmentSessionFixture.update(own, runningSnapshot());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(
				"Not started", WindowTestSupport.find(details, StatusBadge.class).getText());
		assertFalse(WindowTestSupport.text(details).contains("Workspace"));
	}

	public void testCatalogLiveWorkspaceLinkUsesNavigatorAndReportsCleanedWorkspace()
			throws Exception {
		var panel = panel(new AtomicInteger());
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		var run =
				EnvironmentSessionFixture.create(
						getProject(),
						WindowTestSupport.source(),
						WindowTestSupport.scenario(),
						getTestRootDisposable());
		String missing =
				myFixture
						.getTempDirFixture()
						.findOrCreateDir("workspaces")
						.toNioPath()
						.resolve("missing-anvil-workspace")
						.toString();
		var live = runningSnapshot();

				EnvironmentSessionFixture.update(run,
				live.toBuilder()
						.processes(
								List.of(live.getProcesses().getFirst().toBuilder().workDirectory(missing).build()))
						.build());
		panel.controller.execution().showExecution(run);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		var details = WindowTestSupport.find(panel, DefinitionDetailsPanel.class);
		link(details, "missing-anvil-workspace").doClick();
		WindowTestSupport.await(
				() -> WindowTestSupport.text(details).contains("Workspace is no longer available."));
	}

	private static SessionSnapshot runningSnapshot() {
		return SessionSnapshot.builder()
				.state(SessionState.RUNNING)
				.entrypoint("proxy")
				.processes(
						List.of(
								ProcessSnapshot.builder()
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

	private static String scenarioTooltip(ScenarioTreeView tree) {
		Object node = ((DefaultMutableTreeNode) tree.getModel().getRoot()).getFirstChild();
		var renderer =
				(JComponent)
						tree.getCellRenderer()
								.getTreeCellRendererComponent(tree, node, false, true, false, 0, false);
		return renderer.getToolTipText();
	}

	public void testGroupedComponentsAreNotRepeatedAsStandaloneCatalogEntries() throws Exception {
		var panel = panel(new AtomicInteger());
		var network = WindowTestSupport.scenario();
		var groupedServer = network.getProcesses().getFirst();
		var hidden =
				ScenarioDescriptor.builder()
						.definition(network.getDefinition())
						.name("paper")
						.displayName("Paper 1.21.11")
						.entrypoint(groupedServer.getName())
						.process(groupedServer)
						.build();
		var independent = hidden.toBuilder().name("separate").displayName("Separate server").build();
		panel.applyCatalog(List.of(hidden, network, independent));
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
		assertEquals(3, root.getChildCount());
		DefaultMutableTreeNode grouped = null;
		DefaultMutableTreeNode hiddenNode = null;
		DefaultMutableTreeNode independentNode = null;
		for (int index = 0; index < root.getChildCount(); index++) {
			var candidate = (DefaultMutableTreeNode) root.getChildAt(index);
			if (candidate.getUserObject() == network) grouped = candidate;
			if (candidate.getUserObject() == hidden) hiddenNode = candidate;
			if (candidate.getUserObject() == independent) independentNode = candidate;
		}
		assertNotNull(grouped);
		assertNotNull(hiddenNode);
		assertNotNull(independentNode);
		assertEquals(2, grouped.getChildCount());
		tree.setSelectionPath(
				new TreePath(((DefaultMutableTreeNode) grouped.getFirstChild()).getPath()));
		assertEquals(groupedServer, tree.selectedProcess());
		assertEquals(network, tree.selectedScenario());
	}

	public void testSavePreservesSelectedComponentWithoutReplacingWholeScenario() throws Exception {
		var panel = panel(new AtomicInteger());
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		var manager = RunManager.getInstance(getProject());
		WindowTestSupport.performAction(panel, "Save run configuration");
		var whole = (AnvilRunConfiguration) manager.getSelectedConfiguration().getConfiguration();
		assertEquals("", whole.getProcessId());
		tree.expandRow(0);
		tree.setSelectionRow(1);
		WindowTestSupport.performAction(panel, "Save run configuration");
		var component = (AnvilRunConfiguration) manager.getSelectedConfiguration().getConfiguration();
		assertNotSame(whole, component);
		assertEquals("paper", component.getProcessId());
		assertEquals("", whole.getProcessId());
	}

	public void testSearchAndPendingMetadataStayUsable() {
		assertEquals(
				"VELOCITY 3.5.0",
				ScenarioPresentation.platformLabel(WindowTestSupport.scenario().getProcesses().getLast()));
		var pending =
				ScenarioDescriptor.builder()
						.definition("example.Provider")
						.name("plain")
						.displayName("Plain")
						.build();
		var details = new DefinitionDetailsPanel(provider -> {}, workspace -> {});
		details.showScenario(pending);
		assertTrue(WindowTestSupport.text(details).contains("Plain"));
		assertFalse(WindowTestSupport.text(details).contains("null"));
	}

	public void testScenarioCatalogRendersAtCompactAndRegularSizes() throws Exception {
		WindowTestSupport.useDarcula(getTestRootDisposable());
		var panel = panel(new AtomicInteger());
		WindowTestSupport.capture(panel, "anvil-scenarios-1200x400.png", 1200, 400);
		WindowTestSupport.capture(panel, "anvil-scenarios-1000x560.png", 1000, 560);
	}

	public void testStandaloneEnvironmentUsesOneProcessLeafWithoutDuplicateRoleText() {
		var standalone =
				WindowTestSupport.scenario().toBuilder()
						.clearProcesses()
						.process(WindowTestSupport.scenario().getProcesses().getFirst())
						.build();
		var tree = new ScenarioTreeView();
		tree.showScenarios(List.of(standalone), "");
		var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
		assertEquals(0, ((DefaultMutableTreeNode) root.getChildAt(0)).getChildCount());
		assertEquals(standalone, tree.selectedScenario());
		assertEquals(standalone.getProcesses().getFirst(), tree.selectedProcess());
		assertTrue(tree.isEnvironmentSelection());
	}

	public void testSingleProcessWithSetupRemainsAWholeScenario() {
		var scenario =
				WindowTestSupport.scenario().toBuilder()
						.clearProcesses()
						.setupAvailable(true)
						.process(WindowTestSupport.scenario().getProcesses().getFirst())
						.build();
		var tree = new ScenarioTreeView();
		tree.showScenarios(List.of(scenario), "");
		var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
		assertEquals(1, ((DefaultMutableTreeNode) root.getChildAt(0)).getChildCount());
		assertNull(tree.selectedProcess());
		tree.expandRow(0);
		tree.setSelectionRow(1);
		assertEquals(scenario, tree.selectedScenario());
		assertEquals(scenario.getProcesses().getFirst(), tree.selectedProcess());
	}

	public void testSelectedComponentStartsOnlyThatComponent() throws Exception {
		AtomicInteger scenarios = new AtomicInteger();
		AtomicReference<String> component = new AtomicReference<>();
		var panel =
				panel(
						new ScenarioCatalogPanel(
								getProject(),
								(source, scenario) -> scenarios.incrementAndGet(),
								(source, scenario, process) -> component.set(process)));
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		tree.expandRow(0);
		tree.setSelectionRow(1);
		WindowTestSupport.performAction(panel, "Start server");
		assertEquals("paper", component.get());
		assertEquals(0, scenarios.get());
		tree.expandRow(0);
		tree.setSelectionRow(2);
		WindowTestSupport.performAction(panel, "Start proxy");
		assertEquals("proxy", component.get());
		assertEquals(0, scenarios.get());
	}

	public void testLightCatalogUsesNativeTheme() throws Exception {
		WindowTestSupport.useTheme(getTestRootDisposable(), false);
		var panel = panel(new AtomicInteger());
		WindowTestSupport.capture(panel, "anvil-scenarios-light-1200x400.png", 1200, 400);
		WindowTestSupport.capture(panel, "anvil-scenarios-light-1000x560.png", 1000, 560);
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		assertEquals(panel.getBackground(), tree.getBackground());
	}

	private ScenarioCatalogPanel panel(AtomicInteger starts) throws Exception {
		return panel(
				new ScenarioCatalogPanel(
						getProject(),
						(source, scenario) -> starts.incrementAndGet(),
						(source, scenario, process) -> starts.incrementAndGet()));
	}

	private ScenarioCatalogPanel panel(ScenarioCatalogPanel panel) throws Exception {
		Disposer.register(getTestRootDisposable(), panel);
		WindowTestSupport.await(() -> WindowTestSupport.text(panel).contains("Set up Anvil"));
		showDiscovery(
				SourceListing.builder()
						.sources(List.of(WindowTestSupport.source()))
						.status(SourceListingStatus.READY)
						.message("Ready")
						.build(),
				false);
		panel.applyCatalog(List.of(WindowTestSupport.scenario()));
		return panel;
	}
	public void testSourceSelectorAndRefreshUseTheDiscoveryContract() throws Exception {
		var panel = panel(new AtomicInteger());
		var first = WindowTestSupport.source();
		var second = first.toBuilder().id("second").displayName("Second source").build();
		showDiscovery(SourceListing.builder().sources(List.of(first, second))
				.status(SourceListingStatus.READY).message("Ready").build(), false);
		var selector = WindowTestSupport.find(panel, javax.swing.JComboBox.class);
		selector.setSelectedIndex(1);
		assertEquals(second, discovery.snapshot().getSelectedSource());
		WindowTestSupport.performAction(panel, "Load scenarios");
		assertEquals(1, discovery.refreshes);
	}

	public void testStartRechecksDiscoveryStateBeforeTheViewReceivesItsUpdate() throws Exception {
		var starts = new AtomicInteger();
		var panel = panel(starts);
		assertTrue(panel.controller.commands().canStart());
		discovery.value = discovery.value.toBuilder().state(DiscoveryState.SYNCING).build();

		panel.controller.commands().startSelected();
		assertEquals(0, starts.get());
		assertFalse(panel.controller.commands().canStart());

		discovery.value = discovery.value.toBuilder().state(DiscoveryState.IDLE).build();
		panel.controller.commands().startSelected();
		assertEquals(1, starts.get());
	}

	public void testCommandFailureIsClearedWhenSelectingAnotherSource() throws Exception {
		var panel = panel(new ScenarioCatalogPanel(getProject(),
				(source, scenario) -> { throw new IllegalStateException("Fixture launch refused"); },
				(source, scenario, process) -> fail("No process was selected")));
		panel.controller.commands().startSelected();
		assertTrue(WindowTestSupport.text(panel).contains("Fixture launch refused"));

		var source = WindowTestSupport.source().toBuilder().id("replacement").build();
		panel.controller.discovery().select(source);
		assertFalse(WindowTestSupport.text(panel).contains("Fixture launch refused"));
		assertEquals(source, discovery.snapshot().getSelectedSource());
		assertFalse(panel.controller.commands().hasSelection());
		assertFalse(panel.controller.commands().canStart());
	}

	public void testDisposingCatalogReleasesObserversWithoutStoppingItsEnvironment() throws Exception {
		var starts = new AtomicInteger();
		var panel = panel(starts);
		var run = EnvironmentSessionFixture.create(getProject(), WindowTestSupport.source(),
				WindowTestSupport.scenario(), getTestRootDisposable());
		panel.controller.execution().showExecution(run);
		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		String tooltip = scenarioTooltip(tree);
		String details = WindowTestSupport.text(panel.details());
		var queuedDiscoveryUpdate = discovery.listeners.getFirst();
		assertEquals(1, discovery.listeners.size());

		Disposer.dispose(panel);
		assertTrue(discovery.listeners.isEmpty());
		assertTrue("The catalog owns only its session subscription", run.isActive());
		queuedDiscoveryUpdate.run();
		EnvironmentSessionFixture.update(run, runningSnapshot());
		UIUtil.dispatchAllInvocationEvents();
		panel.controller.execution().showExecution(run);
		panel.controller.commands().startSelected();
		panel.controller.discovery().refresh();
		assertFalse(panel.controller.commands().canStart());
		assertFalse(panel.controller.discovery().canRefresh());
		assertEquals(0, starts.get());
		assertEquals(0, discovery.refreshes);
		assertEquals(tooltip, scenarioTooltip(tree));
		assertEquals(details, WindowTestSupport.text(panel.details()));
	}

	public void testReplacingSessionIgnoresAnOlderSessionsQueuedCompletion() throws Exception {
		var panel = panel(new AtomicInteger());
		var source = WindowTestSupport.source();
		var scenario = WindowTestSupport.scenario();
		var first = EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		var second = EnvironmentSessionFixture.create(getProject(), source, scenario, getTestRootDisposable());
		panel.controller.execution().showExecution(first);
		EnvironmentSessionFixture.finish(first, 0);
		EnvironmentSessionFixture.update(second, runningSnapshot());
		panel.controller.execution().showExecution(second);
		UIUtil.dispatchAllInvocationEvents();

		var tree = WindowTestSupport.find(panel, ScenarioTreeView.class);
		assertTrue(scenarioTooltip(tree).contains("Partially running"));
		assertTrue(second.isActive());
		assertTrue(WindowTestSupport.text(panel.details()).contains("Partially running"));
	}

	private final DisplayedDiscovery discovery = new DisplayedDiscovery();

	private void showDiscovery(SourceListing catalog, boolean canSync) {
		discovery.value = DiscoverySnapshot.builder().listing(catalog).canSync(canSync)
				.selectedSource(catalog.getSources().getFirst()).state(DiscoveryState.IDLE).build();
		discovery.listeners.forEach(Runnable::run);
	}

	private static final class DisplayedDiscovery implements SourceDiscovery {
		private int refreshes;
		private final java.util.List<Runnable> listeners = new java.util.ArrayList<>();
		private DiscoverySnapshot value = DiscoverySnapshot.builder()
				.listing(SourceListing.builder().status(SourceListingStatus.UNSUPPORTED).message("Set up Anvil").build())
				.state(DiscoveryState.IDLE).build();

		@Override public void initialize() {}
		@Override public void refresh() { refreshes++; }
		@Override public void sync() {}
		@Override public void select(me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource source) {
			value = value.toBuilder().selectedSource(source).build();
			listeners.forEach(Runnable::run);
		}
		@Override public DiscoverySnapshot snapshot() { return value; }
		@Override public void subscribe(Runnable listener, com.intellij.openapi.Disposable owner) {
			listeners.add(listener);
			Disposer.register(owner, () -> listeners.remove(listener));
		}
	}

}
