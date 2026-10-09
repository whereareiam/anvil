package me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree

import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Catalog tree presentation.
 *
 * Model projection, expansion memory, and rendering are delegated to separate owners.
 */
class ScenarioTreeView : Tree() {
	private val content = ScenarioTreeModel()
	private val expansion = ScenarioTreeExpansion()
	private val renderer = ScenarioTreeRenderer()

	init {
		model = content.model()
		isRootVisible = false
		// JTree declares protected fields with these names; assigning them would bypass the UI update.
		setShowsRootHandles(true)
		setCellRenderer(renderer)
		selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

		addTreeExpansionListener(object : TreeExpansionListener {
			override fun treeExpanded(event: TreeExpansionEvent) = rememberExpansion(event, true)
			override fun treeCollapsed(event: TreeExpansionEvent) = rememberExpansion(event, false)
		})
	}

	fun setAutoExpand(expanded: Boolean) {
		if (!expansion.setAutomatic(expanded)) return
		expansion.restore(this, content.root(), ScenarioTreeModel::identity)
	}

	fun showExecution(scenario: ScenarioDescriptor?, snapshot: SessionSnapshot?, state: EnvironmentState?) {
		renderer.showExecution(scenario, snapshot, state)
		repaint()
	}

	fun showScenarios(scenarios: List<ScenarioDescriptor>, search: String) {
		val selected = selectionId()
		expansion.retain(scenarios.mapTo(HashSet(), ScenarioPresentation::identity))
		content.rebuild(scenarios, search)
		selectionPath = content.pathFor(selected) ?: firstScenarioPath()
		expansion.restore(this, content.root(), ScenarioTreeModel::identity)
	}

	fun selectedScenario(): ScenarioDescriptor? = ScenarioTreeModel.scenario(selectedNode())
	fun selectedProcess(): ProcessDefinition? = ScenarioTreeModel.process(selectedNode())
	fun isEnvironmentSelection(): Boolean = selectedNode()?.userObject is ScenarioDescriptor

	override fun updateUI() {
		super.updateUI()
		background = JBUI.CurrentTheme.ToolWindow.background()
	}

	private fun rememberExpansion(event: TreeExpansionEvent, expanded: Boolean) {
		expansion.remember(event, expanded, ScenarioTreeModel::identity)
	}

	private fun selectedNode(): DefaultMutableTreeNode? = lastSelectedPathComponent as? DefaultMutableTreeNode
	private fun selectionId(): String? = selectedNode()?.let(ScenarioTreeModel::identity)

	private fun firstScenarioPath(): TreePath? {
		val root = content.root()
		if (root.childCount == 0) return null

		return TreePath((root.getChildAt(0) as DefaultMutableTreeNode).path)
	}
}
