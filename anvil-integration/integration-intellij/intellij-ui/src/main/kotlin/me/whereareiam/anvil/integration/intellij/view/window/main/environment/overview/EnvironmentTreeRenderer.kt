package me.whereareiam.anvil.integration.intellij.view.window.main.environment.overview

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusIcon
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusPresentation
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Renders the environment process tree and its live status overlays.
 */
class EnvironmentTreeRenderer : ColoredTreeCellRenderer() {
	override fun customizeCellRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, focus: Boolean) {
		val item = (value as DefaultMutableTreeNode).userObject
		toolTipText = null
		when (item) {
			is EnvironmentController.ProcessItem -> renderProcess(item)
			is EnvironmentController.ScenarioItem -> {
				append(item.name())
				icon = StatusIcon.overlay(
					StatusPresentation.environmentTone(item.status()),
					StatusPresentation.environmentLabel(item.status()),
					ScenarioPresentation.environmentIcon()
				)
				toolTipText = item.description()
			}
		}
		accessibleContext?.accessibleDescription = toolTipText
	}

	private fun renderProcess(item: EnvironmentController.ProcessItem) {
		val reported = item.live()?.state
		val label = StatusPresentation.processLabel(reported, item.session())
		append(item.displayName())
		icon = StatusIcon.overlay(StatusPresentation.processTone(reported, item.session()), label,
			item.definition()?.let { ScenarioPresentation.processIcon(it) } ?: AllIcons.RunConfigurations.Application
		)
		toolTipText = "${item.displayName()} · ${StatusPresentation.processDescription(reported, item.session())}"
	}
}
