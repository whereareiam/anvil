package me.whereareiam.anvil.integration.intellij.runconfiguration

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import javax.swing.JComponent

/**
 * IntelliJ editor adapter around the Kotlin run-configuration form.
 */
class RunConfigurationEditor(owner: Project) : SettingsEditor<AnvilRunConfiguration>() {
	private val configurations = owner.getService(RunConfigurationService::class.java)
	private val form = RunConfigurationForm()

	override fun resetEditorFrom(configuration: AnvilRunConfiguration) {
		val selection = configurations.sourceSelection(configuration.sourceId)
		form.showSources(selection.available(), selection.selected(), selection.message())
		form.reset(configuration.definition, configuration.scenario, configuration.processId)
	}

	@Throws(ConfigurationException::class)
	override fun applyEditorTo(configuration: AnvilRunConfiguration) {
		val selected: ScenarioSource = form.selectedSource()
			?: throw ConfigurationException(form.sourceMessage())

		configuration.sourceId = selected.id
		configuration.definition = form.definition()
		configuration.scenario = form.scenario()
		configuration.processId = form.process()
	}

	override fun createEditor(): JComponent = form.component
}
