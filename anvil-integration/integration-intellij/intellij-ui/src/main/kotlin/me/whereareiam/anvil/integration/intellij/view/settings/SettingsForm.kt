package me.whereareiam.anvil.integration.intellij.view.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.fields.IntegerField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import me.whereareiam.anvil.integration.intellij.model.settings.PreferenceSnapshot
import me.whereareiam.anvil.integration.intellij.type.settings.CommandHistoryPersistence
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection
import java.nio.file.Path
import javax.swing.JComponent

/**
 * Editable form state is local until the Java configurable saves a snapshot.
 */
internal class SettingsForm {
	private var expandScenarioGroups = false
	private var defaultSessionSection = SessionSection.entries.first()
	private var showConsoleOnFailure = false
	private var completedSuccessfulTabs = 0
	private var commandHistoryPersistence = CommandHistoryPersistence.entries.first()
	private var refreshCatalogAfterSync = false
	private var accountsDirectory = ""
	private val retention = IntegerField("Completed successful tabs", 0, Int.MAX_VALUE).apply {
		name = "completedSuccessfulTabs"
		columns = 5
	}
	private lateinit var focus: JComponent
	private lateinit var directory: TextFieldWithBrowseButton

	val component: DialogPanel = panel {
		group("Scenarios") {
			row {
				focus = checkBox("Expand scenarios automatically")
					.bindSelected(::expandScenarioGroups)
					.applyToComponent {
						toolTipText =
							"Expand server and proxy children in the Scenarios view. Running environments always start expanded."
					}.component
			}

			row {
				checkBox("Refresh scenarios after project sync")
					.bindSelected(::refreshCatalogAfterSync)
					.applyToComponent {
						name = "refreshCatalogAfterSync"
						toolTipText =
							"Refresh after a successful sync, waiting until any active environment has finished cleanup."
					}
			}
		}

		group("Run Tabs") {
			row("Default section for new runs:") {
				comboBox(SessionSection.entries)
					.bindItem({ defaultSessionSection }, { if (it != null) defaultSessionSection = it })
					.applyToComponent {
						name = "defaultSessionSection"
						toolTipText =
							"Last used remembers the section separately for each project; the first run opens Environment."
					}
			}

			row {
				checkBox("Show Console when a run fails")
					.bindSelected(::showConsoleOnFailure)
					.applyToComponent {
						name = "showConsoleOnFailure"
						toolTipText =
							"Reveal lifecycle failures and unexpected exits, without switching views for ordinary error log messages."
					}
			}

			row("Keep completed successful tabs:") {
				cell(retention).bindIntText(::completedSuccessfulTabs)
			}.rowComment("0 keeps all successful tabs. Failed runs stay until you close them.")
		}

		group("Console") {
			row("Command history:") {
				comboBox(CommandHistoryPersistence.entries)
					.bindItem({ commandHistoryPersistence }, { if (it != null) commandHistoryPersistence = it })
					.applyToComponent {
						name = "commandHistoryPersistence"
						toolTipText =
							"Keep separate histories for each project, scenario, command target, and command type."
					}
			}.rowComment("History stays separate for each project and command target.")
		}
		group("Accounts") {
			row("Global account directory:") {
				directory = textFieldWithBrowseButton(
					FileChooserDescriptorFactory.createSingleFolderDescriptor()
						.withTitle("Choose Anvil Account Directory")
				)
					.bindText(::accountsDirectory)
					.align(AlignX.FILL)
					.applyToComponent { name = "accountsDirectory" }.component
			}
		}
	}

	fun reset(settings: PreferenceSnapshot, accountsPath: Path) {
		expandScenarioGroups = settings.isExpandScenarioGroups
		defaultSessionSection = settings.defaultSessionSection
		showConsoleOnFailure = settings.isShowConsoleOnFailure
		completedSuccessfulTabs = settings.completedSuccessfulTabs
		commandHistoryPersistence = settings.commandHistoryPersistence
		refreshCatalogAfterSync = settings.isRefreshCatalogAfterSync
		accountsDirectory = accountsPath.toString()
		component.reset()
	}

	fun isModified(): Boolean = component.isModified()

	fun accountDirectory(): String = directory.text.trim()

	fun preferredFocus(): JComponent = focus

	@Throws(ConfigurationException::class)
	fun apply(): PreferenceSnapshot {
		retention.validateContent()
		component.apply()
		return PreferenceSnapshot.builder()
			.expandScenarioGroups(expandScenarioGroups)
			.defaultSessionSection(defaultSessionSection)
			.showConsoleOnFailure(showConsoleOnFailure)
			.completedSuccessfulTabs(completedSuccessfulTabs)
			.commandHistoryPersistence(commandHistoryPersistence)
			.refreshCatalogAfterSync(refreshCatalogAfterSync)
			.accountsDirectory(accountsDirectory.trim())
			.build()
	}
}
