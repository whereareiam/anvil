package me.whereareiam.anvil.integration.intellij.scenario.runconfiguration

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import javax.swing.JComponent

/**
 * IntelliJ editor adapter around [RunConfigurationForm].
 */
class RunConfigurationEditor(private val project: Project) : SettingsEditor<AnvilRunConfiguration>() {
	private val form = RunConfigurationForm()

	override fun resetEditorFrom(configuration: AnvilRunConfiguration) {
		val choice = RunConfigurationService.getInstance(project).sourceChoice(configuration.sourceId)
		form.showSources(choice.available(), choice.selected(), choice.hint())
		form.reset(configuration.definition, configuration.processId)
	}

	@Throws(ConfigurationException::class)
	override fun applyEditorTo(configuration: AnvilRunConfiguration) {
		val selected = form.selectedSource() ?: throw ConfigurationException(form.sourceHint())

		configuration.sourceId = selected.id
		configuration.definition = form.definition()
		configuration.processId = form.process()
	}

	override fun createEditor(): JComponent = form.component
}
