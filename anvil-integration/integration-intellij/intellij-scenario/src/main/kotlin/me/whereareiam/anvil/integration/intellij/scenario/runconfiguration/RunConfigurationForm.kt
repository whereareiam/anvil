package me.whereareiam.anvil.integration.intellij.scenario.runconfiguration

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import javax.swing.JList

/**
 * Run configuration fields: the scenario source, the definition class, and an optional process.
 */
internal class RunConfigurationForm {
	private val source = ComboBox<ScenarioSource>().apply {
		renderer = object : SimpleListCellRenderer<ScenarioSource>() {
			override fun customize(list: JList<out ScenarioSource>, value: ScenarioSource?, index: Int, selected: Boolean, hasFocus: Boolean) {
				text = value?.displayName ?: "Choose a scenario source"
			}
		}
	}
	private val sourceHint = JBLabel()
	private val definition = JBTextField().apply { emptyText.text = "com.example.RegistrationScenario" }
	private val process = JBTextField().apply { emptyText.text = "Whole scenario" }

	val component = panel {
		row("Scenario source:") { cell(source).align(AlignX.FILL) }
		row("") { cell(sourceHint) }
		row("Definition class:") { cell(definition).align(AlignX.FILL) }
			.rowComment("The class that implements AnvilScenarioDefinition.")
		row("Process:") { cell(process).align(AlignX.FILL) }
			.rowComment("Start only this server or proxy and the processes it depends on.")
	}

	fun showSources(available: List<ScenarioSource>, selected: ScenarioSource?, hint: String) {
		source.removeAllItems()
		available.forEach(source::addItem)
		source.selectedItem = selected
		source.isEnabled = available.size > 1 || selected == null && available.isNotEmpty()
		sourceHint.text = hint
	}

	fun reset(definitionClass: String, processId: String) {
		definition.text = definitionClass
		process.text = processId
	}

	fun selectedSource(): ScenarioSource? = source.selectedItem as ScenarioSource?
	fun sourceHint(): String = sourceHint.text
	fun definition(): String = definition.text.trim()
	fun process(): String = process.text.trim()
}
