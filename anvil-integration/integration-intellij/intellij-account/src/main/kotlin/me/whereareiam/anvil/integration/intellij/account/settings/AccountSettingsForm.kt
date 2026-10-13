package me.whereareiam.anvil.integration.intellij.account.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Editable account directories; [AccountSettingsPage] resolves, validates, and stores them.
 */
class AccountSettingsForm(project: Project) {
	private var globalDefault = ""
	private var projectDefault = ""
	private val global = TextFieldWithBrowseButton().apply {
		addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
		textField.accessibleContext.accessibleName = "Global account directory"
	}
	private val own = TextFieldWithBrowseButton().apply {
		addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
		textField.accessibleContext.accessibleName = "Project account directory"
	}
	private val includeGlobal = JBCheckBox("Use global accounts in this project")

	val component: JComponent = panel {
		group("Global Accounts") {
			row("Directory:") {
				cell(global).align(AlignX.FILL)
					.comment("Shared by every project on this computer.")
			}
			row {
				link("Use default") { global.text = globalDefault }
			}
		}
		group("This Project") {
			row("Directory:") {
				cell(own).align(AlignX.FILL)
					.comment("Kept outside the project by default, so credentials are never committed.")
			}
			row {
				link("Use default") { own.text = projectDefault }
			}
			row { cell(includeGlobal) }
		}
		row {
			comment("Changing a directory does not move existing account files.")
		}
	}

	fun globalDirectory(): String = global.text.trim()

	fun projectDirectory(): String = own.text.trim()

	fun includesGlobal(): Boolean = includeGlobal.isSelected

	fun preferredFocus(): JComponent = global.textField

	fun reset(globalDirectory: String, projectDirectory: String, includesGlobal: Boolean, globalDefault: String, projectDefault: String) {
		this.globalDefault = globalDefault
		this.projectDefault = projectDefault
		global.text = globalDirectory
		own.text = projectDirectory
		includeGlobal.isSelected = includesGlobal
	}
}
