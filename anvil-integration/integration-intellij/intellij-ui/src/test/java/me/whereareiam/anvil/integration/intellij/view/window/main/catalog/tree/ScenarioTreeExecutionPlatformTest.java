package me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.icons.IconWithToolTip;

import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentStateCalculator;
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusIcon;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusPresentation;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class ScenarioTreeExecutionPlatformTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	public void testPreparingAndPartialStartUpdateRootAndExactComponentWithoutExtraText() {
		ScenarioDescriptor scenario = network();
		ScenarioTreeView tree = tree(scenario);
		DefaultMutableTreeNode group = node(tree, 0);
		String firstText = render(tree, child(group, 0)).getCharSequence(false).toString();
		showExecution(tree, scenario, snapshot(SessionState.STARTING));
		assertStatus(tree, group, "Starting");
		for (int index = 0; index < 3; index++) assertStatus(tree, child(group, index), "Not started");

		showExecution(tree, scenario, snapshot(SessionState.STARTING, live("paper", "STARTING")));
		assertStatus(tree, group, "Starting");
		assertStatus(tree, child(group, 0), "Starting");
		assertStatus(tree, child(group, 1), "Not started");

		showExecution(tree, scenario, snapshot(SessionState.STARTING, live("paper", "READY")));
		assertStatus(tree, group, "Starting");
		assertStatus(tree, child(group, 0), "Running");
		assertStatus(tree, child(group, 1), "Not started");
		assertStatus(tree, child(group, 2), "Not started");
		assertEquals(scenario.getDisplayName(), render(tree, group).getCharSequence(false).toString());
		assertEquals(firstText, render(tree, child(group, 0)).getCharSequence(false).toString());
		assertTrue(render(tree, group).getToolTipText().contains(scenario.getDescription()));
		assertTrue(
				render(tree, child(group, 0))
						.getToolTipText()
						.contains(scenario.getProcesses().getFirst().getDescription()));
		assertEquals(
				render(tree, group).getToolTipText(),
				render(tree, group).getAccessibleContext().getAccessibleDescription());

		showExecution(tree, scenario, snapshot(SessionState.RUNNING, live("paper", "READY")));
		assertStatus(tree, group, "Partially running");
	}

	public void testExecutionMatchesExactProviderAndScenarioIdentity() {
		ScenarioDescriptor scenario = network();
		ScenarioDescriptor otherProvider = scenario.toBuilder().definition("other.Provider").build();
		ScenarioDescriptor otherScenario = scenario.toBuilder().name("other-network").build();
		ScenarioTreeView tree = tree(scenario, otherProvider, otherScenario);
		showExecution(tree,
				scenario.toBuilder().displayName("A changed display label").build(),
				snapshot(SessionState.RUNNING, live("paper", "READY")));
		assertStatus(tree, node(tree, 0), "Partially running");
		assertSame(ScenarioPresentation.environmentIcon(), render(tree, node(tree, 1)).getIcon());
		assertSame(ScenarioPresentation.environmentIcon(), render(tree, node(tree, 2)).getIcon());
		assertEquals(otherProvider.getDescription(), render(tree, node(tree, 1)).getToolTipText());
	}

	public void testStatusUpdatesPreserveNodesSelectionExpansionAndSearchResults() {
		ScenarioDescriptor scenario = network();
		ScenarioDescriptor other =
				scenario.toBuilder().name("other-network").displayName("Another network").build();
		ScenarioTreeView tree = tree(scenario, other);
		DefaultMutableTreeNode first = node(tree, 0);
		DefaultMutableTreeNode second = node(tree, 1);
		TreePath selection = new TreePath(child(first, 2).getPath());
		tree.expandPath(new TreePath(first.getPath()));
		tree.setSelectionPath(selection);
		tree.collapsePath(new TreePath(second.getPath()));
		int rows = tree.getRowCount();
		showExecution(tree, scenario, snapshot(SessionState.STARTING));
		showExecution(tree, scenario, snapshot(SessionState.RUNNING, live("proxy", "READY")));
		assertSame(first, node(tree, 0));
		assertSame(second, node(tree, 1));
		assertEquals(selection, tree.getSelectionPath());
		assertFalse(tree.isExpanded(new TreePath(second.getPath())));
		assertEquals(rows, tree.getRowCount());

		tree.showScenarios(List.of(scenario, other), "Another");
		DefaultMutableTreeNode filtered = node(tree, 0);
		showExecution(tree, scenario, snapshot(SessionState.RUNNING, live("paper", "READY")));
		assertEquals(1, ((DefaultMutableTreeNode) tree.getModel().getRoot()).getChildCount());
		assertSame(filtered, node(tree, 0));
		assertSame(ScenarioPresentation.environmentIcon(), render(tree, filtered).getIcon());
	}

	public void testClearingExecutionRemovesOverlaysAndStoppedRecordsNeverLookRunning() {
		ScenarioDescriptor scenario = network();
		ScenarioTreeView tree = tree(scenario);
		DefaultMutableTreeNode group = node(tree, 0);
		showExecution(tree, scenario, snapshot(SessionState.STOPPING, live("paper", "READY")));
		assertStatus(tree, group, "Stopping");
		assertStatus(tree, child(group, 0), "Stopping");
		showExecution(tree, scenario, snapshot(SessionState.STOPPED, live("paper", "READY")));
		assertStatus(tree, group, "Stopped");
		assertStatus(tree, child(group, 0), "Stopped");
		showExecution(tree, null, null);
		assertSame(ScenarioPresentation.environmentIcon(), render(tree, group).getIcon());
		assertEquals(scenario.getDescription(), render(tree, group).getToolTipText());
		assertFalse(render(tree, child(group, 0)).getToolTipText().contains("Running"));

		showExecution(tree, scenario, snapshot(SessionState.RUNNING, live("paper", "READY")));
		showExecution(tree, null, snapshot(SessionState.RUNNING, live("paper", "READY")));
		assertSame(ScenarioPresentation.environmentIcon(), render(tree, group).getIcon());
		showExecution(tree, scenario, null);
		assertSame(ScenarioPresentation.environmentIcon(), render(tree, group).getIcon());
	}

	public void testStatusOverlaysKeepServerAndProxyRoleArtworkIncludingStandaloneLeaves() {
		ScenarioDescriptor scenario = network();
		ScenarioTreeView tree = tree(scenario);
		showExecution(tree,
				scenario, snapshot(SessionState.RUNNING, live("paper", "READY"), live("proxy", "READY")));
		DefaultMutableTreeNode group = node(tree, 0);
		assertIcon(
				tree, child(group, 0), StatusIcon.overlay(StatusPresentation.Tone.RUNNING, "Running", ScenarioPresentation.processIcon(ProcessRole.SERVER)));
		assertIcon(
				tree, child(group, 2), StatusIcon.overlay(StatusPresentation.Tone.RUNNING, "Running", ScenarioPresentation.processIcon(ProcessRole.PROXY)));
		assertIcon(tree, group, StatusIcon.overlay(StatusPresentation.Tone.TRANSITION, "Partially running", ScenarioPresentation.environmentIcon()));
		for (int index : List.of(0, 2)) {
			var process = scenario.getProcesses().get(index);
			var standalone =
					scenario.toBuilder().name(process.getName()).clearProcesses().process(process).build();
			tree.showScenarios(List.of(standalone), "");
			showExecution(tree, standalone, snapshot(SessionState.STARTING));
			assertEquals(0, node(tree, 0).getChildCount());
			assertIcon(tree, node(tree, 0), StatusIcon.overlay(StatusPresentation.Tone.TRANSITION, "Starting", ScenarioPresentation.processIcon(process)));
		}
	}

	public void testPartialExecutionCatalogRendersInNativeDarkAndLightThemes() throws Exception {
		ScenarioDescriptor scenario = network();
		for (boolean dark : List.of(true, false)) {
			WindowTestSupport.useTheme(getTestRootDisposable(), dark);
			ScenarioTreeView tree =
					tree(
							scenario,
							scenario.toBuilder().name("another").displayName("Another environment").build());
			showExecution(tree, scenario, snapshot(SessionState.RUNNING, live("paper", "READY")));
			WindowTestSupport.capture(
					tree, "anvil-catalog-execution-" + (dark ? "dark" : "light") + ".png", 640, 280);
		}
	}

	private static ScenarioDescriptor network() {
		var base = WindowTestSupport.scenario();
		return base.toBuilder()
				.clearProcesses()
				.process(base.getProcesses().getFirst())
				.process(
						base.getProcesses().getFirst().toBuilder()
								.name("game")
								.displayName("Game backend")
								.build())
				.process(base.getProcesses().getLast())
				.build();
	}

	private static SessionSnapshot snapshot(SessionState state, ProcessSnapshot... processes) {
		return SessionSnapshot.builder().state(state).processes(List.of(processes)).build();
	}

	private static ProcessSnapshot live(String name, String state) {
		return ProcessSnapshot.builder()
				.name(name)
				.executionId(EnvironmentSessionFixture.executionId(name))
				.displayName(name)
				.state(ProcessState.fromWireValue(state))
				.host("127.0.0.1")
				.port(25565)
				.workDirectory("/workspace/" + name)
				.build();
	}

	private static void showExecution(ScenarioTreeView tree, ScenarioDescriptor scenario, SessionSnapshot snapshot) {
		if (scenario == null || snapshot == null) {
			tree.showExecution(scenario, snapshot, null);
			return;
		}
		tree.showExecution(scenario, snapshot, EnvironmentStateCalculator.calculate(scenario, snapshot));
	}

	private static ScenarioTreeView tree(ScenarioDescriptor... scenarios) {
		ScenarioTreeView tree = new ScenarioTreeView();
		tree.showScenarios(List.of(scenarios), "");
		return tree;
	}

	private static DefaultMutableTreeNode node(ScenarioTreeView tree, int index) {
		return child((DefaultMutableTreeNode) tree.getModel().getRoot(), index);
	}

	private static DefaultMutableTreeNode child(DefaultMutableTreeNode parent, int index) {
		return (DefaultMutableTreeNode) parent.getChildAt(index);
	}

	private static ColoredTreeCellRenderer render(
			ScenarioTreeView tree, DefaultMutableTreeNode node) {
		TreePath path = new TreePath(node.getPath());
		return (ColoredTreeCellRenderer)
				tree.getCellRenderer()
						.getTreeCellRendererComponent(
								tree,
								node,
								false,
								tree.isExpanded(path),
								node.isLeaf(),
								tree.getRowForPath(path),
								false);
	}

	private static void assertStatus(
			ScenarioTreeView tree, DefaultMutableTreeNode node, String text) {
		var renderer = render(tree, node);
		assertTrue(renderer.getIcon() instanceof IconWithToolTip);
		assertEquals(text, ((IconWithToolTip) renderer.getIcon()).getToolTip(true));
		assertTrue(renderer.getToolTipText().contains(text));
	}

	private static void assertIcon(
			ScenarioTreeView tree, DefaultMutableTreeNode node, Icon expected) {
		var renderer = render(tree, node);
		assertEquals(expected.getIconWidth(), renderer.getIcon().getIconWidth());
		assertEquals(expected.getIconHeight(), renderer.getIcon().getIconHeight());
		assertTrue(
				"State overlays must preserve the role artwork",
				Arrays.equals(pixels(expected, renderer), pixels(renderer.getIcon(), renderer)));
	}

	private static int[] pixels(Icon icon, Component owner) {
		BufferedImage image =
				new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		var painter = image.createGraphics();
		try {
			icon.paintIcon(owner, painter, 0, 0);
		} finally {
			painter.dispose();
		}
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}
}
