package me.whereareiam.anvil.integration.intellij.scenario.catalog.view.tree

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.scenario.window.ScenarioPresentation
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusIcon
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusPresentation
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import java.util.Locale
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Renders scenario and process nodes, including live execution state and accessible descriptions.
 */
class ScenarioTreeRenderer : ColoredTreeCellRenderer() {
	private var executionScenario: ScenarioDescriptor? = null
	private var executionSnapshot: SessionSnapshot? = null
	private var executionEnvironmentState: EnvironmentSession.Status? = null

	fun showExecution(scenario: ScenarioDescriptor?, snapshot: SessionSnapshot?, state: EnvironmentSession.Status?) {
		executionScenario = scenario
		executionSnapshot = snapshot
		executionEnvironmentState = state
	}

	override fun customizeCellRenderer(
		tree: JTree,
		value: Any,
		selected: Boolean,
		expanded: Boolean,
		leaf: Boolean,
		row: Int,
		focus: Boolean
	) {
		val node = value as DefaultMutableTreeNode
		when (val item = node.userObject) {
			is ScenarioDescriptor -> renderScenario(item)
			is ProcessDefinition -> renderProcess(node, item)
		}

		accessibleContext?.accessibleDescription = toolTipText
	}

	private fun renderScenario(scenario: ScenarioDescriptor) {
		append(scenario.displayName)
		val process = ScenarioPresentation.standaloneProcess(scenario)
		icon = process?.let(ScenarioPresentation::processIcon) ?: ScenarioPresentation.scenarioIcon()
		if (process != null) appendPlatform(scenario.displayName, process)

		toolTipText = scenario.description
		if (executionFor(scenario) == null) return
		val state = executionEnvironmentState ?: return
		val label = StatusPresentation.environmentLabel(state)
		icon = StatusIcon.overlay(StatusPresentation.environmentTone(state), label, icon)
		toolTipText = tooltip(scenario.description, label)
	}

	private fun renderProcess(node: DefaultMutableTreeNode, process: ProcessDefinition) {
		append(process.displayName)
		appendPlatform(process.displayName, process)
		icon = ScenarioPresentation.processIcon(process)
		toolTipText = tooltip(process.description, ScenarioPresentation.platformLabel(process))

		val scenario = (node.parent as? DefaultMutableTreeNode)?.userObject as? ScenarioDescriptor ?: return
		val snapshot = executionFor(scenario) ?: return
		val state = snapshot.processes.firstOrNull { it.name == process.name }?.state
		val label = StatusPresentation.processLabel(state, snapshot.state)
		icon = StatusIcon.overlay(StatusPresentation.processTone(state, snapshot.state), label, icon)
		toolTipText = tooltip(toolTipText, StatusPresentation.processDescription(state, snapshot.state))
	}

	private fun executionFor(scenario: ScenarioDescriptor): SessionSnapshot? {
		val current = executionScenario ?: return null
		if (current.definition != scenario.definition) return null

		return executionSnapshot
	}

	private fun appendPlatform(displayName: String, process: ProcessDefinition) {
		val platform = ScenarioPresentation.platformLabel(process)
		if (!displayName.lowercase(Locale.ROOT).contains(platform.lowercase(Locale.ROOT)))
			append("  $platform", SimpleTextAttributes.GRAYED_ATTRIBUTES)
	}

	private fun tooltip(purpose: String?, detail: String): String =
		if (purpose.isNullOrBlank()) detail else "$purpose · $detail"
}
