package me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.OnePixelSplitter;

import java.util.List;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentStateCalculator;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.overview.EnvironmentPanel;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class ScenarioTreeExpansionPlatformTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ServiceContainerUtil.replaceService(
				ApplicationManager.getApplication(),
				Preferences.class,
				new PersistentPreferences(),
				getTestRootDisposable());
	}

	public void testCatalogGroupsStartCollapsedAndKeepManualChoicesDuringRefreshAndStatusUpdates() {
		var first = WindowTestSupport.scenario();
		var second = first.toBuilder().name("second").displayName("Second scenario").build();
		ScenarioTreeView tree = new ScenarioTreeView();
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
		var first = WindowTestSupport.scenario();
		var second = first.toBuilder().name("second").displayName("Second scenario").build();
		ScenarioTreeView tree = new ScenarioTreeView();
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
		var first = WindowTestSupport.scenario();
		var second = first.toBuilder().name("second").build();
		ScenarioTreeView tree = new ScenarioTreeView();
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
		var first = WindowTestSupport.scenario();
		var second = first.toBuilder().definition("another.Provider").build();
		ScenarioTreeView tree = new ScenarioTreeView();
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
						WindowTestSupport.source(),
						WindowTestSupport.scenario(),
						getTestRootDisposable());
		EnvironmentPanel panel = new EnvironmentPanel(getProject(), run);
		panel.update();
		JTree tree = WindowTestSupport.find(panel, JTree.class);
		assertEquals(3, tree.getRowCount());
		assertTrue(tree.isExpanded(0));
		assertEquals(
				0.25f, WindowTestSupport.find(panel, OnePixelSplitter.class).getProportion(), 0.001f);
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
						WindowTestSupport.source(),
						WindowTestSupport.scenario(),
						getTestRootDisposable());
		EnvironmentPanel panel = new EnvironmentPanel(getProject(), run);
		panel.update();
		JTree tree = WindowTestSupport.find(panel, JTree.class);
		PersistentPreferences.getInstance().setExpandScenarioGroups(true);
		assertTrue(tree.isExpanded(0));
		tree.collapseRow(0);
		PersistentPreferences.getInstance().setExpandScenarioGroups(false);
		assertFalse(
				"Changing catalog settings must keep the environment's manual collapse",
				tree.isExpanded(0));
		PersistentPreferences.getInstance().setExpandScenarioGroups(true);
		assertFalse(tree.isExpanded(0));
		EnvironmentSessionFixture.update(run, snapshot(SessionState.RUNNING));
		panel.update();
		assertFalse(tree.isExpanded(0));
		var other =
				EnvironmentSessionFixture.create(
						getProject(),
						WindowTestSupport.source(),
						WindowTestSupport.scenario(),
						getTestRootDisposable());
		EnvironmentPanel another = new EnvironmentPanel(getProject(), other);
		another.update();
		assertTrue(WindowTestSupport.find(another, JTree.class).isExpanded(0));
		PersistentPreferences.getInstance().setExpandScenarioGroups(false);
		assertFalse(tree.isExpanded(0));
		assertTrue(WindowTestSupport.find(another, JTree.class).isExpanded(0));
	}

	public void testColdRunExpandsWhenItsCatalogFirstAddsAGroupRegardlessOfCatalogPreference() {
		ScenarioDescriptor complete = WindowTestSupport.scenario();
		ScenarioDescriptor pending =
				ScenarioDescriptor.builder()
						.definition(complete.getDefinition())
						.name(complete.getName())
						.displayName(complete.getDisplayName())
						.build();
		for (boolean automatic : List.of(false, true)) {
			PersistentPreferences.getInstance().setExpandScenarioGroups(automatic);
			var run =
					EnvironmentSessionFixture.create(
							getProject(), WindowTestSupport.source(), pending, getTestRootDisposable());
			EnvironmentPanel panel = new EnvironmentPanel(getProject(), run);
			panel.update();
			JTree tree = WindowTestSupport.find(panel, JTree.class);
			assertEquals(1, tree.getRowCount());
			EnvironmentSessionFixture.scenario(run, complete);
			panel.update();
			assertTrue(tree.isExpanded(0));
			assertEquals(3, tree.getRowCount());
		}
	}

	private static void showExecution(ScenarioTreeView tree, ScenarioDescriptor scenario, SessionSnapshot snapshot) {
		tree.showExecution(scenario, snapshot, EnvironmentStateCalculator.calculate(scenario, snapshot));
	}

	private static TreePath group(ScenarioTreeView tree, int index) {
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
