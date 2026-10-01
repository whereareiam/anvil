package me.whereareiam.anvil.integration.intellij.view.window.main;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.icons.IconWithToolTip;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.console.ConsolePanel;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.overview.EnvironmentPanel;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class SidebarRowsPlatformTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	public void testSidebarLabelsStayConciseWhileStatusRemainsAccessible() {
		var run = run(WindowTestSupport.scenario());
		var environment = new EnvironmentPanel(getProject(), run);
		environment.update();
		var console = new ConsolePanel(run);
		JTree tree = WindowTestSupport.find(environment, JTree.class);
		tree.expandRow(0);
		JList<?> sources = WindowTestSupport.find(console, JList.class);
		for (int index : List.of(1, 2)) {
			String name = run.getScenario().getProcesses().get(index - 1).getDisplayName();
			for (SimpleColoredComponent renderer :
					List.of(treeRow(tree, index), listRow(sources, index))) {
				assertEquals(name, renderer.getCharSequence(false).toString());
				assertTrue(renderer.getToolTipText().contains("Running"));
				assertTrue(renderer.getAccessibleContext().getAccessibleDescription().contains("Running"));
				assertEquals("Running", ((IconWithToolTip) renderer.getIcon()).getToolTip(true));
			}
		}

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().state(SessionState.STOPPED).build());
		environment.update();
		console.update();
		assertEquals("Paper backend", treeRow(tree, 1).getCharSequence(false).toString());
		assertEquals("Paper backend", listRow(sources, 1).getCharSequence(false).toString());
		assertTrue(treeRow(tree, 1).getToolTipText().contains("Stopped"));
		assertTrue(listRow(sources, 1).getToolTipText().contains("Stopped"));
	}

	public void testConsoleRowsStayFlatWithEnvironmentOuterMarginsAcrossNativeThemes()
			throws Exception {
		boolean previous = UISettings.getInstance().getCompactTreeIndents();
		try {
			for (String theme :
					List.of("ExperimentalDark", "ExperimentalLight", "Islands Dark", "Islands Light")) {
				if (UiThemeProviderListManager.Companion.getInstance().findThemeById(theme) == null)
					continue;
				WindowTestSupport.useTheme(getTestRootDisposable(), theme, theme.endsWith("Dark"));
				for (boolean compact : List.of(false, true)) {
					UISettings.getInstance().setCompactTreeIndents(compact);
					var run = run(WindowTestSupport.scenario());
					var environment = new EnvironmentPanel(getProject(), run);
					environment.update();
					var console = new ConsolePanel(run);
					JPanel views = new JPanel(new GridLayout(1, 2));
					views.add(environment);
					views.add(console);
					SwingUtilities.updateComponentTreeUI(views);
					JTree tree = WindowTestSupport.find(environment, JTree.class);
					tree.expandRow(0);
					WindowTestSupport.capture(
							views,
							"anvil-sidebar-alignment-"
									+ theme.replace(' ', '-').toLowerCase()
									+ (compact ? "-compact" : "")
									+ ".png",
							1200,
							400);
					JList<?> sources = WindowTestSupport.find(console, JList.class);
					assertEquals(
							"Both sidebars share the theme's outer margin",
							tree.getInsets().left,
							sources.getInsets().left);
					RowBounds first = bounds(listRow(sources, 0), sources.getCellBounds(0, 0));
					assertEquals(
							"Console content must start at the outer margin without a tree gutter",
							sources.getInsets().left,
							first.iconX);
					for (int index : List.of(0, 1, 2)) {
						RowBounds consoleRow =
								bounds(listRow(sources, index), sources.getCellBounds(index, index));
						assertEquals(
								theme + "/" + compact + " flat icon alignment at row " + index,
								first.iconX,
								consoleRow.iconX);
						assertEquals(
								theme + "/" + compact + " flat text alignment at row " + index,
								first.textX,
								consoleRow.textX);
					}
				}
			}
		} finally {
			UISettings.getInstance().setCompactTreeIndents(previous);
		}
	}

	public void testStandaloneConsoleUsesTheSameFlatRowsAndOuterMargin() throws Exception {
		WindowTestSupport.useTheme(getTestRootDisposable(), true);
		var base = WindowTestSupport.scenario();
		var scenario =
				base.toBuilder().clearProcesses().process(base.getProcesses().getFirst()).build();
		var run = run(scenario);
		var environment = new EnvironmentPanel(getProject(), run);
		environment.update();
		var console = new ConsolePanel(run);
		JPanel views = new JPanel(new GridLayout(1, 2));
		views.add(environment);
		views.add(console);
		WindowTestSupport.capture(views, "anvil-sidebar-alignment-standalone.png", 1200, 400);
		JTree tree = WindowTestSupport.find(environment, JTree.class);
		JList<?> sources = WindowTestSupport.find(console, JList.class);
		assertEquals(1, tree.getRowCount());
		assertEquals(tree.getInsets().left, sources.getInsets().left);
		RowBounds all = bounds(listRow(sources, 0), sources.getCellBounds(0, 0));
		RowBounds consoleRow = bounds(listRow(sources, 1), sources.getCellBounds(1, 1));
		assertEquals(all, consoleRow);
	}

	private RetainedEnvironmentSession run(ScenarioDescriptor scenario) {
		var run =
				EnvironmentSessionFixture.create(
						getProject(), WindowTestSupport.source(), scenario, getTestRootDisposable());

				EnvironmentSessionFixture.update(run,
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.setupComplete(true)
						.processes(
								scenario.getProcesses().stream()
										.map(
												process ->
														ProcessSnapshot.builder()
																.name(process.getName())
																.executionId(EnvironmentSessionFixture.executionId(process.getName()))
																.displayName(process.getDisplayName())
																.state(ProcessState.READY)
																.host("127.0.0.1")
																.port(25565)
																.workDirectory("/work/" + process.getName())
																.build())
										.toList())
						.build());
		return run;
	}

	private static SimpleColoredComponent treeRow(JTree tree, int index) {
		var path = tree.getPathForRow(index);
		return (SimpleColoredComponent)
				tree.getCellRenderer()
						.getTreeCellRendererComponent(
								tree,
								path.getLastPathComponent(),
								false,
								tree.isExpanded(path),
								tree.getModel().isLeaf(path.getLastPathComponent()),
								index,
								false);
	}

	private static <T> SimpleColoredComponent listRow(JList<T> list, int index) {
		return (SimpleColoredComponent)
				list.getCellRenderer()
						.getListCellRendererComponent(
								list, list.getModel().getElementAt(index), index, false, false);
	}

	private static RowBounds bounds(SimpleColoredComponent renderer, Rectangle cell) {
		assertNotNull(cell);
		Icon original = renderer.getIcon();
		int[] iconX = {-1};
		renderer.setIcon(
				new Icon() {
					@Override
					public void paintIcon(Component component, Graphics graphics, int x, int y) {
						iconX[0] = x;
						original.paintIcon(component, graphics, x, y);
					}

					@Override
					public int getIconWidth() {
						return original.getIconWidth();
					}

					@Override
					public int getIconHeight() {
						return original.getIconHeight();
					}
				});
		Dimension preferred = renderer.getPreferredSize();
		renderer.setSize(preferred);
		BufferedImage image =
				new BufferedImage(preferred.width, preferred.height, BufferedImage.TYPE_INT_ARGB);
		var painter = image.createGraphics();
		try {
			renderer.paint(painter);
		} finally {
			painter.dispose();
			renderer.setIcon(original);
		}
		assertTrue("The native renderer must paint its role icon", iconX[0] >= 0);
		for (int x = 0; x < preferred.width; x++)
			if (renderer.findFragmentAt(x) >= 0) return new RowBounds(cell.x + iconX[0], cell.x + x);
		throw new AssertionError("The native renderer must expose its label bounds");
	}

	private record RowBounds(int iconX, int textX) {}
}
