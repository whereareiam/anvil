package me.whereareiam.anvil.integration.intellij.scenario.settings

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Editable scenario settings; values stay local until [ScenarioSettingsPage] applies them.
 */
internal class ScenarioSettingsForm {
	private var expandScenarios = false
	private var refreshAfterSync = false
	private var openingSection = ScenarioSettings.Section.LAST_USED
	private var revealConsoleOnFailure = false
	private var completedTabs = 0
	private var commandHistory = ScenarioSettings.History.SESSION
	private val retention = IntegerField("Completed successful tabs", 0, Int.MAX_VALUE).apply {
		name = "completedTabs"
		columns = 5
	}
	private lateinit var focus: JComponent

	val component: DialogPanel = panel {
		group("Catalog") {
			row {
				focus = checkBox("Expand scenarios automatically")
					.bindSelected(::expandScenarios)
					.comment("Show each scenario's servers and proxies. Running scenarios always start expanded.")
					.component
			}
			row {
				checkBox("Reload scenarios after project sync")
					.bindSelected(::refreshAfterSync)
					.comment("Waits until a running environment has stopped.")
					.applyToComponent { name = "refreshAfterSync" }
			}
		}

		group("Environment Tabs") {
			row("Open new tabs on:") {
				comboBox(ScenarioSettings.Section.entries)
					.bindItem({ openingSection }, { if (it != null) openingSection = it })
					.applyToComponent { name = "openingSection" }
			}.rowComment("Last used remembers the section for each project; the first run opens Environment.")
			row {
				checkBox("Show the console when an environment fails")
					.bindSelected(::revealConsoleOnFailure)
					.applyToComponent { name = "revealConsoleOnFailure" }
			}
			row("Keep tabs of successful runs:") {
				cell(retention).bindIntText(::completedTabs)
			}.rowComment("0 keeps all of them. Failed runs stay until you close them.")
		}

		group("Console") {
			row("Remember commands:") {
				comboBox(ScenarioSettings.History.entries)
					.bindItem({ commandHistory }, { if (it != null) commandHistory = it })
					.applyToComponent { name = "commandHistory" }
			}.rowComment("Each project, scenario, and command target keeps its own history.")
		}
	}

	fun reset(values: ScenarioSettings.Values) {
		expandScenarios = values.isExpandScenarios
		refreshAfterSync = values.isRefreshAfterSync
		openingSection = values.openingSection
		revealConsoleOnFailure = values.isRevealConsoleOnFailure
		completedTabs = values.completedTabs
		commandHistory = values.commandHistory
		component.reset()
	}

	fun isModified(): Boolean = component.isModified()

	fun preferredFocus(): JComponent = focus

	@Throws(ConfigurationException::class)
	fun apply(): ScenarioSettings.Values {
		retention.validateContent()
		component.apply()
		return ScenarioSettings.Values.builder()
			.expandScenarios(expandScenarios)
			.refreshAfterSync(refreshAfterSync)
			.openingSection(openingSection)
			.revealConsoleOnFailure(revealConsoleOnFailure)
			.completedTabs(completedTabs)
			.commandHistory(commandHistory)
			.build()
	}
}
