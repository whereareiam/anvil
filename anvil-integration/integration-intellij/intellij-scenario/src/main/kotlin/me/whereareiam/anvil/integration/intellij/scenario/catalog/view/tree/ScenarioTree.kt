package me.whereareiam.anvil.integration.intellij.scenario.catalog.view.tree

import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.scenario.window.ScenarioPresentation
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Catalog tree: scenarios with their servers and proxies, filtered by the search text, with live status overlays.
 *
 * Model projection, expansion memory, and rendering are delegated to separate owners. Typing in the tree
 * jumps to a matching scenario or process.
 */
class ScenarioTree : Tree() {
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
		getAccessibleContext().accessibleName = "Scenarios"
		emptyText.text = "No scenario matches the filter"
		TreeSpeedSearch.installOn(this, false) { path -> label(path.lastPathComponent as? DefaultMutableTreeNode) }

		addTreeExpansionListener(object : TreeExpansionListener {
			override fun treeExpanded(event: TreeExpansionEvent) = rememberExpansion(event, true)
			override fun treeCollapsed(event: TreeExpansionEvent) = rememberExpansion(event, false)
		})
	}

	fun setAutoExpand(expanded: Boolean) {
		if (!expansion.setAutomatic(expanded)) return
		expansion.restore(this, content.root(), ScenarioTreeModel::identity)
	}

	fun showExecution(scenario: ScenarioDescriptor?, snapshot: SessionSnapshot?, status: EnvironmentSession.Status?) {
		renderer.showExecution(scenario, snapshot, status)
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

	private fun label(node: DefaultMutableTreeNode?): String? = when (val value = node?.userObject) {
		is ScenarioDescriptor -> value.displayName
		is ProcessDefinition -> value.displayName
		else -> null
	}
}
