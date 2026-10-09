package me.whereareiam.anvil.integration.intellij.view.window.account

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import javax.swing.Action
import javax.swing.JComponent

/**
 * Account window presentation; project account operations live in the Java controller.
 */
class ProjectAccountsDialog(
	project: Project,
	source: ScenarioSource?
) : DialogWrapper(project, false) {
	private lateinit var controller: ProjectAccountsController

	init {
		controller = ProjectAccountsController(project, source) { isDisposed }
		title = "Project Accounts · ${project.name}"
		setCancelButtonText("Close")
		init()
	}

	override fun createActions(): Array<Action> = arrayOf(cancelAction)
	override fun createCenterPanel(): JComponent = controller.component()
}
