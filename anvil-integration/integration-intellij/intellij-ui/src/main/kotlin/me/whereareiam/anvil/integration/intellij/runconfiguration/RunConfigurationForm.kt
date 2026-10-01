package me.whereareiam.anvil.integration.intellij.runconfiguration

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import javax.swing.JList

internal class RunConfigurationForm {
	private val source = ComboBox<ScenarioSource>().apply {
		renderer = object : SimpleListCellRenderer<ScenarioSource>() {
			override fun customize(
				list: JList<out ScenarioSource>,
				value: ScenarioSource?,
				index: Int,
				selected: Boolean,
				hasFocus: Boolean
			) {
				text = value?.displayName ?: "Choose a scenario source"
			}
		}
	}
	private val sourceStatus = JBLabel()
	private val definition = JBTextField()
	private val scenario = JBTextField()
	private val process = JBTextField().apply { emptyText.text = "Whole scenario" }

	val component = panel {
		row("Scenario source:") { cell(source).align(AlignX.FILL) }
		row { cell(sourceStatus) }
		row("Definition class:") { cell(definition).align(AlignX.FILL) }
		row("Scenario ID:") { cell(scenario).align(AlignX.FILL) }
		row("Process ID (optional):") { cell(process).align(AlignX.FILL) }
	}

	fun showSources(available: List<ScenarioSource>, selected: ScenarioSource?, message: String) {
		source.removeAllItems()
		available.forEach(source::addItem)
		source.selectedItem = selected
		source.isEnabled = available.size > 1 || selected == null && available.isNotEmpty()
		sourceStatus.text = message
	}

	fun reset(definitionId: String, scenarioId: String, processId: String) {
		definition.text = definitionId
		scenario.text = scenarioId
		process.text = processId
	}

	fun selectedSource(): ScenarioSource? = source.selectedItem as ScenarioSource?
	fun sourceMessage(): String = sourceStatus.text
	fun definition(): String = definition.text.trim()
	fun scenario(): String = scenario.text.trim()
	fun process(): String = process.text.trim()
}
