package me.whereareiam.anvil.integration.intellij.view.window.main.component.details;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.View;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusBadge;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class DefinitionDetailsPanelPlatformTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	public void testReadableSectionsUseTwoBoundedColumnsAndOneAtNarrowWidths() {
		DefinitionDetailsPanel details = details();
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		details.showProcess(
				scenario,
				scenario.getProcesses().getLast(),
				live("proxy", "/work/proxy"),
				SessionState.RUNNING);

		arrange(details, 1500);
		JPanel overview = section(details, "Overview");
		JPanel runtime = section(details, "Runtime");
		JPanel distribution = section(details, "Distribution");
		assertEquals(overview.getY(), runtime.getY());
		assertTrue(runtime.getX() > overview.getX());
		assertEquals(runtime.getX(), distribution.getX());
		assertTrue(distribution.getY() >= runtime.getY() + runtime.getHeight());
		assertTrue(
				"Large tool windows should retain readable line lengths",
				runtime.getX() + runtime.getWidth() <= JBUI.scale(1016));
		assertFactsUseAlignedRows(overview);
		assertNoOverlap(details);
		int wideColumnWidth = overview.getWidth();

		arrange(details, 1100);
		assertEquals(
				"The content remains bounded as the tool window widens",
				wideColumnWidth,
				overview.getWidth());
		assertNoOverlap(details);

		arrange(details, 360);
		assertEquals(overview.getX(), runtime.getX());
		assertTrue(runtime.getY() >= overview.getY() + overview.getHeight());
		assertTrue(distribution.getY() >= runtime.getY() + runtime.getHeight());
		assertFactsUseAlignedRows(overview);
		assertNoOverlap(details);
		assertTrue(details.getScrollableTracksViewportWidth());
		assertFalse(details.getScrollableTracksViewportHeight());
	}

	public void testLongestLabelWidensTheSharedColumnInsteadOfStacking() throws Exception {
		WindowTestSupport.useTheme(getTestRootDisposable(), true);
		DefinitionDetailsPanel details = details();
		details.showScenario(WindowTestSupport.scenario());

		arrange(details, 760);

		assertTrue(WindowTestSupport.text(details).contains("Shutdown timeout"));
		assertFactsUseAlignedRows(section(details, "Runtime"));
		assertFactsUseAlignedRows(section(details, "Overview"));
	}

	public void testDefinitionAndWorkspaceLinksNavigateAndCopyTheirFullTargets() throws Exception {
		String definition =
				"me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioCatalog";
		String workspace = "/home/developer/projects/" + "integration-workspaces/".repeat(8) + "proxy";
		AtomicReference<String> openedDefinition = new AtomicReference<>();
		AtomicReference<String> openedWorkspace = new AtomicReference<>();
		DefinitionDetailsPanel details =
				new DefinitionDetailsPanel(openedDefinition::set, openedWorkspace::set);
		ScenarioDescriptor scenario =
				WindowTestSupport.scenario().toBuilder().definition(definition).build();
		details.showProcess(
				scenario,
				scenario.getProcesses().getLast(),
				live("proxy", workspace),
				SessionState.RUNNING);
		arrange(details, 360);

		LinkLabel<?> definitionLink = link(details, "CompatibilityScenarioCatalog");
		LinkLabel<?> workspaceLink = link(details, "proxy");
		assertEquals(definition, definitionLink.getToolTipText());
		assertEquals(workspace, workspaceLink.getToolTipText());
		assertFalse(
				"Qualified names should not stretch the inspector",
				WindowTestSupport.text(details).contains(definition));
		assertFalse(
				"Workspace paths should not occupy several rows",
				WindowTestSupport.text(details).contains(workspace));
		definitionLink.doClick();
		workspaceLink.doClick();
		assertEquals(definition, openedDefinition.get());
		assertEquals(workspace, openedWorkspace.get());
		definitionLink.getActionMap().get("copyTarget").actionPerformed(null);
		assertEquals(definition, CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
		workspaceLink.getActionMap().get("copyTarget").actionPerformed(null);
		assertEquals(workspace, CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
		assertEquals("Definition", definitionLink.getAccessibleContext().getAccessibleName());
		assertEquals("Workspace", workspaceLink.getAccessibleContext().getAccessibleName());
		assertNoOverlap(details);
	}

	public void testLongValuesWrapWithoutGrowingTheInspectorAndRemainCopyable() {
		String distribution =
				"Local JAR: /home/developer/projects/"
						+ "integration-distributions/".repeat(16)
						+ "paper.jar";
		String description =
				"Inspect this configured server and its complete launch inputs. ".repeat(8);
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		var process =
				scenario.getProcesses().getFirst().toBuilder()
						.distribution(distribution)
						.description(description)
						.build();
		DefinitionDetailsPanel details = details();
		details.showProcess(scenario, process, live("paper", "/work/paper"), SessionState.RUNNING);

		arrange(details, 700);
		JTextArea path = value(details, distribution);
		int wideHeight = path.getHeight();
		assertFalse(path.isEditable());
		path.selectAll();
		assertEquals(distribution, path.getSelectedText());
		assertTextFits(path);
		assertNoOverlap(details);

		arrange(details, 300);
		assertTrue("Narrow values must wrap into more lines", path.getHeight() > wideHeight);
		assertTextFits(path);
		assertTextFits(value(details, description));
		assertEquals(distribution, path.getText());
		assertNoOverlap(details);
		Dimension measured = details.getPreferredSize();
		assertEquals(
				"Repeated measurement must not change layout hints", measured, details.getPreferredSize());

		arrange(details, 700);
		assertEquals(
				"Returning to the same width restores the same wrapped height",
				wideHeight,
				path.getHeight());
	}

	public void testUnchangedModelKeepsTextSelectionAndPendingIdentityReportsNoFakeCounts() {
		DefinitionDetailsPanel details = details();
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		details.showScenario(scenario);
		JTextArea name = value(details, scenario.getName());
		name.select(0, 7);
		details.showScenario(scenario.toBuilder().build());
		assertSame(name, value(details, scenario.getName()));
		assertEquals(scenario.getName().substring(0, 7), name.getSelectedText());

		details.showScenario(
				ScenarioDescriptor.builder()
						.definition("example.Provider")
						.name("pending")
						.displayName("Pending")
						.build());
		String text = WindowTestSupport.text(details);
		assertTrue(text.contains("Waiting for the project scenarios"));
		assertFalse(text.contains("Automated"));
		assertFalse(text.contains("null"));
		assertFalse(text.contains("Processes"));
		assertFalse(text.contains("0 seconds"));
		assertFalse(text.contains("Category"));
		assertFalse(text.contains("Runtime"));
	}

	public void testLiveScenarioRendersInNativeDarkAndLightThemesAtWideAndNarrowWidths()
			throws Exception {
		WindowTestSupport.useTheme(getTestRootDisposable(), true);
		DefinitionDetailsPanel details = details();
		ScenarioDescriptor scenario =
				WindowTestSupport.scenario().toBuilder()
						.definition(
								"me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioCatalog")
						.name("velocity-paper-1.21.11")
						.displayName("Velocity with Paper 1.21.11")
						.description("A Velocity proxy wired to separate lobby and game servers.")
						.category("Proxy networks")
						.build();
		details.showEnvironment(
				scenario,
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.entrypoint("proxy")
						.processes(List.of(live("proxy", "/work/proxy")))
						.build(),
				EnvironmentState.PARTIALLY_RUNNING);
		for (int width : new int[] {1500, 1100, 760, 360}) {
			arrange(details, width);
			assertTheme(details, scenario);
			assertNoOverlap(details);
			WindowTestSupport.capture(
					details,
					"anvil-details-scenario-dark-" + width + ".png",
					width,
					details.getPreferredSize().height);
		}

		WindowTestSupport.useTheme(getTestRootDisposable(), false);
		SwingUtilities.updateComponentTreeUI(details);
		for (int width : new int[] {1500, 760, 360}) {
			arrange(details, width);
			assertTheme(details, scenario);
			assertNoOverlap(details);
			WindowTestSupport.capture(
					details,
					"anvil-details-scenario-light-" + width + ".png",
					width,
					details.getPreferredSize().height);
		}
	}

	public void testPartialEnvironmentKeepsDeclaredTopologyAndPendingSetupSeparate() {
		DefinitionDetailsPanel details = details();
		ScenarioDescriptor scenario =
				WindowTestSupport.scenario().toBuilder().setupAvailable(true).build();
		SessionSnapshot partial =
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.entrypoint("proxy")
						.processes(List.of(live("paper", "/work/paper")))
						.build();
		details.showEnvironment(scenario, partial, EnvironmentState.PARTIALLY_RUNNING);
		String text = WindowTestSupport.text(details);
		assertTrue(text.contains("1 of 2 running"));
		assertTrue(text.contains("Partially running"));
		assertFalse(text.contains("RUNNING"));
		assertTrue(text.contains("Pending full scenario start"));
		assertFalse(text.contains("Join address"));
		details.showEnvironment(
				scenario,
				partial.toBuilder()
						.processes(
								List.of(
										live("paper", "/work/paper"),
										live("proxy", "/work/proxy").toBuilder().state(ProcessState.STOPPED).build()))
						.build(),
				EnvironmentState.PARTIALLY_RUNNING);
		assertFalse(
				"A retained stopped entrypoint is not a joinable address",
				WindowTestSupport.text(details).contains("Join address"));

		details.showEnvironment(
				scenario,
				partial.toBuilder()
						.setupComplete(true)
						.processes(List.of(live("paper", "/work/paper"), live("proxy", "/work/proxy")))
						.build(),
				EnvironmentState.RUNNING);
		assertTrue(WindowTestSupport.text(details).contains("Completed"));
		assertTrue(WindowTestSupport.text(details).contains("Join address"));

		details.showProcess(scenario, scenario.getProcesses().getLast(), null, SessionState.IDLE);
		assertTrue(WindowTestSupport.text(details).contains("VELOCITY"));
		assertFalse(WindowTestSupport.text(details).contains("Address"));
		assertFalse(WindowTestSupport.text(details).contains("Workspace"));
	}

	public void testGroupedProcessInspectorRendersAtRegularAndNarrowWidths() throws Exception {
		WindowTestSupport.useTheme(getTestRootDisposable(), true);
		DefinitionDetailsPanel details = details();
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		details.showProcess(
				scenario,
				scenario.getProcesses().getLast(),
				live("proxy", "/work/player-registration/proxy"),
				SessionState.RUNNING);
		for (int width : new int[] {1100, 760, 360}) {
			arrange(details, width);
			assertNoOverlap(details);
			WindowTestSupport.capture(
					details,
					"anvil-details-process-" + width + ".png",
					width,
					details.getPreferredSize().height);
		}
		WindowTestSupport.useTheme(getTestRootDisposable(), false);
		SwingUtilities.updateComponentTreeUI(details);
		for (int width : new int[] {1100, 360}) {
			arrange(details, width);
			assertNoOverlap(details);
			WindowTestSupport.capture(
					details,
					"anvil-details-process-light-" + width + ".png",
					width,
					details.getPreferredSize().height);
		}
	}

	public void testNavigationFeedbackWrapsAndClearsWithTheSelection() {
		DefinitionDetailsPanel details = details();
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		details.showScenario(scenario);
		String status = "Workspace is no longer available. Anvil may have cleaned it up after the run.";
		details.showNavigationStatus(status);
		arrange(details, 360);
		assertTextFits(value(details, status));
		assertNoOverlap(details);
		details.showNavigationStatus("Opened workspace in Project.");
		assertFalse(WindowTestSupport.text(details).contains(status));
		details.showProcess(scenario, scenario.getProcesses().getLast(), null, SessionState.IDLE);
		assertFalse(WindowTestSupport.text(details).contains("Opened workspace"));
	}

	public void testCompletedRunDoesNotAdvertiseRetainedReadyProcessesOrJoinAddress() {
		DefinitionDetailsPanel details = details();
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		var process = live("proxy", "/work/proxy");
		var snapshot =
				SessionSnapshot.builder()
						.state(SessionState.STOPPED)
						.entrypoint("proxy")
						.processes(List.of(process))
						.build();
		details.showEnvironment(scenario, snapshot, EnvironmentState.STOPPED);
		assertTrue(WindowTestSupport.text(details).contains("0 of 2 running"));
		assertFalse(WindowTestSupport.text(details).contains("Join address"));
		details.showProcess(scenario, scenario.getProcesses().getLast(), process, SessionState.RUNNING);
		assertTrue(WindowTestSupport.text(details).contains("Running"));
		details.showProcess(scenario, scenario.getProcesses().getLast(), process, SessionState.STOPPED);
		assertTrue(WindowTestSupport.text(details).contains("Stopped"));
		assertFalse(WindowTestSupport.text(details).contains("Running"));
		details.showEnvironment(scenario, snapshot.toBuilder().state(SessionState.FAILED).build(), EnvironmentState.FAILED);
		assertTrue(WindowTestSupport.text(details).contains("2 configured"));
		assertFalse(WindowTestSupport.text(details).contains("Join address"));
	}

	public void testStatusTagsKeepTheirNaturalWidthAndMoveBelowTheLabelWhenNarrow() throws Exception {
		ScenarioDescriptor scenario = WindowTestSupport.scenario();
		var snapshot =
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.processes(List.of(live("paper", "/work/paper")))
						.build();
		for (boolean dark : List.of(true, false)) {
			WindowTestSupport.useTheme(getTestRootDisposable(), dark);
			DefinitionDetailsPanel details = details();
			details.showEnvironment(scenario, snapshot, EnvironmentState.PARTIALLY_RUNNING);
			for (int width : new int[] {1100, 360, 320, 280, 240}) {
				arrange(details, width);
				StatusBadge badge = WindowTestSupport.find(details, StatusBadge.class);
				assertEquals(
						"The status tag must neither stretch nor truncate",
						badge.getPreferredSize(),
						badge.getSize());
				assertEquals("Partially running", badge.getText());
				Component label = badge.getParent().getComponent(0);
				if (width == 240) {
					assertEquals(label.getX(), badge.getX());
					assertTrue(
							"A narrow status belongs below its label",
							badge.getY() >= label.getY() + label.getHeight());
				} else if (width >= 360) assertEquals(label.getY(), badge.getY());
				assertNoOverlap(details);
				if (width <= 360)
					WindowTestSupport.capture(
							details,
							"anvil-details-status-" + (dark ? "dark" : "light") + "-" + width + ".png",
							width,
							details.getHeight());
			}
			details.showProcess(
					scenario,
					scenario.getProcesses().getLast(),
					live("proxy", "/work/proxy"),
					SessionState.FAILED);
			arrange(details, 240);
			StatusBadge badge = WindowTestSupport.find(details, StatusBadge.class);
			assertEquals("Status unavailable", badge.getText());
			assertEquals(badge.getPreferredSize(), badge.getSize());
			assertNoOverlap(details);
		}
	}

	private static DefinitionDetailsPanel details() {
		return new DefinitionDetailsPanel(provider -> {}, workspace -> {});
	}

	private static ProcessSnapshot live(String name, String workspace) {
		return ProcessSnapshot.builder()
				.name(name)
				.executionId(EnvironmentSessionFixture.executionId(name))
				.displayName(name)
				.state(ProcessState.READY)
				.host("127.0.0.1")
				.port(25565)
				.workDirectory(workspace)
				.build();
	}

	private static void arrange(DefinitionDetailsPanel details, int width) {
		details.setSize(width, 1);
		details.setSize(width, details.getPreferredSize().height);
		layout(details);
		assertEquals(details.getHeight(), details.getPreferredSize().height);
	}

	private static void layout(Container container) {
		container.doLayout();
		for (Component component : container.getComponents())
			if (component instanceof Container child) layout(child);
	}

	private static JPanel section(DefinitionDetailsPanel details, String heading) {
		for (Component component : details.getComponents())
			if (component instanceof JPanel panel
					&& panel.getComponentCount() > 0
					&& panel.getComponent(0) instanceof TitledSeparator separator
					&& separator.getText().equals(heading)) return panel;
		throw new AssertionError("Missing section " + heading);
	}

	private static LinkLabel<?> link(Container container, String text) {
		for (Component component : container.getComponents()) {
			if (component instanceof LinkLabel<?> label && label.getText().equals(text)) return label;
			if (component instanceof Container child) {
				try {
					return link(child, text);
				} catch (AssertionError ignored) {
				}
			}
		}
		throw new AssertionError("Missing link " + text);
	}

	private static JTextArea value(Container container, String text) {
		JTextArea result = findValue(container, text);
		if (result != null) return result;
		throw new AssertionError("Missing value " + text);
	}

	private static JTextArea findValue(Container container, String text) {
		for (Component component : container.getComponents()) {
			if (component instanceof JTextArea area && area.getText().equals(text)) return area;
			if (component instanceof Container child) {
				JTextArea result = findValue(child, text);
				if (result != null) return result;
			}
		}
		return null;
	}

	private static void assertFactsUseAlignedRows(JPanel section) {
		int valueColumn = -1;
		for (Component child : section.getComponents()) {
			if (!(child instanceof JPanel fact)
					|| fact.getComponentCount() != 2
					|| fact instanceof TitledSeparator) continue;
			Component label = fact.getComponent(0);
			Component value = fact.getComponent(1);
			assertEquals("Labels and values should share a row", label.getY(), value.getY());
			assertTrue(value.getX() > label.getX() + label.getWidth());
			if (valueColumn >= 0) assertEquals("Value columns should align", valueColumn, value.getX());
			valueColumn = value.getX();
		}
	}

	private static void assertTextFits(JTextArea area) {
		var insets = area.getInsets();
		View view = area.getUI().getRootView(area);
		view.setSize(area.getWidth() - insets.left - insets.right, Integer.MAX_VALUE);
		assertTrue(
				"The whole wrapped value must remain visible",
				area.getHeight()
						>= Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom);
	}

	private static void assertTheme(DefinitionDetailsPanel details, ScenarioDescriptor scenario) {
		assertEquals(JBUI.CurrentTheme.ToolWindow.background(), details.getBackground());
		assertEquals(UIUtil.getLabelForeground(), value(details, scenario.getName()).getForeground());
		assertEquals(
				UIUtil.getContextHelpForeground(),
				value(details, scenario.getDescription()).getForeground());
		assertEquals(UIUtil.getLabelFont(), value(details, scenario.getName()).getFont());
	}

	private static void assertNoOverlap(Container root) {
		List<Component> leaves = new ArrayList<>();
		collect(root, leaves);
		for (int first = 0; first < leaves.size(); first++) {
			Component current = leaves.get(first);
			if (current instanceof JLabel && !(current instanceof LinkLabel<?>))
				assertTrue(
						"Fact labels must remain fully readable: " + current,
						current.getPreferredSize().width <= current.getWidth());
			Rectangle bounds =
					SwingUtilities.convertRectangle(current.getParent(), current.getBounds(), root);
			assertTrue(
					current.toString(),
					bounds.x >= 0
							&& bounds.y >= 0
							&& bounds.x + bounds.width <= root.getWidth()
							&& bounds.y + bounds.height <= root.getHeight());
			for (int second = first + 1; second < leaves.size(); second++) {
				Component next = leaves.get(second);
				Rectangle other = SwingUtilities.convertRectangle(next.getParent(), next.getBounds(), root);
				assertFalse("Facts overlap: " + current + " / " + next, bounds.intersects(other));
			}
		}
	}

	private static void collect(Container root, List<Component> leaves) {
		for (Component component : root.getComponents()) {
			if (!component.isVisible()) continue;
			if (component instanceof JLabel || component instanceof JTextArea) leaves.add(component);
			else if (component instanceof Container child) collect(child, leaves);
		}
	}
}
