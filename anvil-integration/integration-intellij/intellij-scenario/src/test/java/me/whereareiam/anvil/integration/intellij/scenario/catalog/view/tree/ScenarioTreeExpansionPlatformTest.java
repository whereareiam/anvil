package me.whereareiam.anvil.integration.intellij.scenario.catalog.view.tree;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.OnePixelSplitter;

import java.util.List;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import me.whereareiam.anvil.integration.intellij.ScenarioFixtures;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.overview.OverviewPanel;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class ScenarioTreeExpansionPlatformTest extends ScenarioPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ServiceContainerUtil.replaceService(
				ApplicationManager.getApplication(),
				ScenarioSettings.class,
				new ScenarioSettings(),
				getTestRootDisposable());
	}

	public void testCatalogGroupsStartCollapsedAndKeepManualChoicesDuringRefreshAndStatusUpdates() {
		var first = ScenarioFixtures.scenario();
		var second = first.toBuilder().definition("example.SecondScenario").name("second").displayName("Second scenario").build();
		ScenarioTree tree = new ScenarioTree();
		tree.showScenarios(List.of(first, second), "");
		assertEquals(2, tree.getRowCount());
		assertFalse(tree.isExpanded(group(tree, 0)));
		assertFalse(tree.isExpanded(group(tree, 1)));
		tree.expandPath(group(tree, 0));
		tree.setSelectionRow(1);
		showExecution(tree, first, snapshot(SessionState.STARTING));
		assertTrue(tree.isExpanded(group(tree, 0)));
		tree.showScenarios(
				List.of(first.toBuilder().displayName("Renamed scenario").build(), second), "");
		assertTrue(tree.isExpanded(group(tree, 0)));
		assertFalse(tree.isExpanded(group(tree, 1)));
		assertEquals("paper", tree.selectedProcess().getName());
		tree.collapsePath(group(tree, 0));
		tree.showScenarios(List.of(first, second), "");
		assertFalse(tree.isExpanded(group(tree, 0)));
		assertEquals(2, tree.getRowCount());
	}

	public void testSearchKeepsEachGroupsChoiceAndRestoresItWhenSearchClears() {
		var first = ScenarioFixtures.scenario();
		var second = first.toBuilder().definition("example.SecondScenario").name("second").displayName("Second scenario").build();
		ScenarioTree tree = new ScenarioTree();
		tree.showScenarios(List.of(first, second), "");
		tree.expandPath(group(tree, 0));
		tree.showScenarios(List.of(first, second), "Second scenario");
		assertFalse(tree.isExpanded(group(tree, 0)));
		assertEquals(1, tree.getRowCount());
		tree.showScenarios(List.of(first, second), "");
		assertTrue(tree.isExpanded(group(tree, 0)));
		assertFalse(tree.isExpanded(group(tree, 1)));
		tree.showScenarios(List.of(first, second), "Second scenario");
		tree.expandPath(group(tree, 0));
		tree.showScenarios(List.of(first, second), "");
		assertTrue(tree.isExpanded(group(tree, 0)));
		assertTrue(tree.isExpanded(group(tree, 1)));
	}

	public void testExpansionPreferenceChangesExistingGroupsAndDefaultsForNewGroups() {
		var first = ScenarioFixtures.scenario();
		var second = first.toBuilder().definition("example.SecondScenario").name("second").build();
		ScenarioTree tree = new ScenarioTree();
		tree.showScenarios(List.of(first), "");
		tree.setAutoExpand(true);
		assertTrue(tree.isExpanded(group(tree, 0)));
		tree.collapsePath(group(tree, 0));
		tree.setAutoExpand(true);
		assertFalse(
				"Applying an unchanged preference keeps a manual choice", tree.isExpanded(group(tree, 0)));
		tree.showScenarios(List.of(first, second), "");
		assertFalse(
				"A manual collapse survives refresh even with automatic expansion enabled",
				tree.isExpanded(group(tree, 0)));
		assertTrue("New groups follow the preference", tree.isExpanded(group(tree, 1)));
		tree.setAutoExpand(false);
		assertFalse(tree.isExpanded(group(tree, 0)));
		assertFalse(tree.isExpanded(group(tree, 1)));
		tree.setAutoExpand(true);
		assertTrue(tree.isExpanded(group(tree, 0)));
		assertTrue(tree.isExpanded(group(tree, 1)));
	}

	public void testSameScenarioNameFromDifferentProvidersKeepsIndependentExpansion() {
		var first = ScenarioFixtures.scenario();
		var second = first.toBuilder().definition("another.Provider").build();
		ScenarioTree tree = new ScenarioTree();
		tree.showScenarios(List.of(first, second), "");
		tree.expandPath(group(tree, 1));
		tree.showScenarios(List.of(second, first), "");
		assertTrue(tree.isExpanded(group(tree, 0)));
		assertFalse(tree.isExpanded(group(tree, 1)));
	}

	public void testEnvironmentStartsExpandedAndPreservesManualChoicesAcrossSnapshots() {
		var run =
				EnvironmentSessionFixture.create(
						getProject(),
						ScenarioFixtures.source(),
						ScenarioFixtures.scenario(),
						getTestRootDisposable());
		OverviewPanel panel = new OverviewPanel(getProject(), run);
		panel.update();
		JTree tree = SwingTestSupport.find(panel, JTree.class);
		assertEquals(3, tree.getRowCount());
		assertTrue(tree.isExpanded(0));
		assertEquals(
				0.25f, SwingTestSupport.find(panel, OnePixelSplitter.class).getProportion(), 0.001f);
		tree.setSelectionRow(1);
		EnvironmentSessionFixture.update(run, snapshot(SessionState.RUNNING));
		panel.update();
		assertTrue(tree.isExpanded(0));
		assertEquals(1, tree.getSelectionRows()[0]);
		tree.collapseRow(0);
		EnvironmentSessionFixture.update(run, snapshot(SessionState.STOPPING));
		panel.update();
		assertFalse(tree.isExpanded(0));
		assertEquals(1, tree.getRowCount());
	}

	public void testCatalogPreferenceDoesNotChangeExistingOrNewEnvironmentViews() {
		var run =
				EnvironmentSessionFixture.create(
						getProject(),
						ScenarioFixtures.source(),
						ScenarioFixtures.scenario(),
						getTestRootDisposable());
		OverviewPanel panel = new OverviewPanel(getProject(), run);
		panel.update();
		JTree tree = SwingTestSupport.find(panel, JTree.class);
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().expandScenarios(true).build());
		assertTrue(tree.isExpanded(0));
		tree.collapseRow(0);
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().expandScenarios(false).build());
		assertFalse(
				"Changing catalog settings must keep the environment's manual collapse",
				tree.isExpanded(0));
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().expandScenarios(true).build());
		assertFalse(tree.isExpanded(0));
		EnvironmentSessionFixture.update(run, snapshot(SessionState.RUNNING));
		panel.update();
		assertFalse(tree.isExpanded(0));
		var other =
				EnvironmentSessionFixture.create(
						getProject(),
						ScenarioFixtures.source(),
						ScenarioFixtures.scenario(),
						getTestRootDisposable());
		OverviewPanel another = new OverviewPanel(getProject(), other);
		another.update();
		assertTrue(SwingTestSupport.find(another, JTree.class).isExpanded(0));
		ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().expandScenarios(false).build());
		assertFalse(tree.isExpanded(0));
		assertTrue(SwingTestSupport.find(another, JTree.class).isExpanded(0));
	}

	public void testColdRunExpandsWhenItsCatalogFirstAddsAGroupRegardlessOfCatalogPreference() {
		ScenarioDescriptor complete = ScenarioFixtures.scenario();
		ScenarioDescriptor pending =
				ScenarioDescriptor.builder()
						.definition(complete.getDefinition())
						.name(complete.getName())
						.displayName(complete.getDisplayName())
						.build();
		for (boolean automatic : List.of(false, true)) {
			ScenarioSettings.getInstance().update(ScenarioSettings.getInstance().values().toBuilder().expandScenarios(automatic).build());
			var run =
					EnvironmentSessionFixture.create(
							getProject(), ScenarioFixtures.source(), pending, getTestRootDisposable());
			OverviewPanel panel = new OverviewPanel(getProject(), run);
			panel.update();
			JTree tree = SwingTestSupport.find(panel, JTree.class);
			assertEquals(1, tree.getRowCount());
			EnvironmentSessionFixture.scenario(run, complete);
			panel.update();
			assertTrue(tree.isExpanded(0));
			assertEquals(3, tree.getRowCount());
		}
	}

	private static void showExecution(ScenarioTree tree, ScenarioDescriptor scenario, SessionSnapshot snapshot) {
		tree.showExecution(scenario, snapshot, EnvironmentSession.Status.of(scenario, snapshot));
	}

	private static TreePath group(ScenarioTree tree, int index) {
		var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
		return new TreePath(((DefaultMutableTreeNode) root.getChildAt(index)).getPath());
	}

	private static SessionSnapshot snapshot(SessionState state) {
		return SessionSnapshot.builder()
				.state(state)
				.processes(
						List.of(
								ProcessSnapshot.builder()
										.name("paper")
										.executionId(EnvironmentSessionFixture.executionId("paper"))
										.displayName("Paper backend")
										.state(ProcessState.READY)
										.host("127.0.0.1")
										.port(25565)
										.workDirectory("/workspace/paper")
										.build()))
				.build();
	}
}
