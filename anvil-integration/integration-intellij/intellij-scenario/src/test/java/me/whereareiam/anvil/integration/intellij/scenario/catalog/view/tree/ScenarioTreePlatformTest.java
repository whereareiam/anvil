package me.whereareiam.anvil.integration.intellij.scenario.catalog.view.tree;

import java.awt.Component;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.ScenarioFixtures;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;

public class ScenarioTreePlatformTest extends ScenarioPlatformTestCase {
	public void testRowsAreLaidOutWithScenarioRenderer() throws Exception {
		SwingTestSupport.useTheme(getTestRootDisposable(), true);
		var tree = new ScenarioTree();
		tree.showScenarios(List.of(ScenarioFixtures.scenario()), "");

		var path = tree.getPathForRow(0);
		Component rendered = tree.getCellRenderer()
				.getTreeCellRendererComponent(tree, path.getLastPathComponent(), false, false, false, 0, false);

		assertTrue(tree.getCellRenderer() instanceof ScenarioTreeRenderer);
		assertTrue(tree.getShowsRootHandles());
		assertEquals(rendered.getPreferredSize().width, tree.getPathBounds(path).width);
	}
}
