package me.whereareiam.anvil.integration.intellij.account.view

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import javax.swing.Action
import javax.swing.JComponent

/**
 * Account window; account and pool operations live in [AccountsController].
 */
class AccountsDialog(project: Project, source: ScenarioSource?) : DialogWrapper(project, false) {
	private val controller: AccountsController

	init {
		controller = AccountsController(project, source) { isDisposed }
		title = "Anvil Accounts · ${project.name}"
		setCancelButtonText("Close")
		init()
	}

	override fun createActions(): Array<Action> = arrayOf(cancelAction)
	override fun createCenterPanel(): JComponent = controller.component()
	override fun getDimensionServiceKey() = "Anvil.Accounts"
}
