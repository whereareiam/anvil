package me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree;

import java.awt.Component;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;

public class ScenarioTreeViewPlatformTest extends UiPlatformTestCase {
	public void testRowsAreLaidOutWithScenarioRenderer() throws Exception {
		WindowTestSupport.useTheme(getTestRootDisposable(), true);
		var tree = new ScenarioTreeView();
		tree.showScenarios(List.of(WindowTestSupport.scenario()), "");

		var path = tree.getPathForRow(0);
		Component rendered = tree.getCellRenderer()
				.getTreeCellRendererComponent(tree, path.getLastPathComponent(), false, false, false, 0, false);

		assertTrue(tree.getCellRenderer() instanceof ScenarioTreeRenderer);
		assertTrue(tree.getShowsRootHandles());
		assertEquals(rendered.getPreferredSize().width, tree.getPathBounds(path).width);
	}
}
