package me.whereareiam.anvil.integration.intellij.view.window.account

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import me.whereareiam.anvil.integration.intellij.account.authentication.AccountAuthenticator
import me.whereareiam.anvil.integration.intellij.account.authentication.AccountEnrollment
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.Action
import javax.swing.JComponent

/**
 * Presents account enrollment while the Java controller owns the asynchronous operation.
 */
class AuthenticationDialog(
	project: Project,
	accountId: String,
	factory: Function<Consumer<String>, out AccountEnrollment>,
	changed: Runnable
) : DialogWrapper(project, false) {
	private val form = AuthenticationForm()
	private val controller: AuthenticationController

	constructor(project: Project, source: ScenarioSource, accountId: String, changed: Runnable) : this(
		project,
		accountId,
		Function { output -> project.getService(AccountAuthenticator::class.java).create(source, accountId, output) },
		changed
	)

	init {
		controller = AuthenticationController(project, accountId, factory, form, changed) { setCancelButtonText("Close") }
		title = "Sign in · $accountId"
		setCancelButtonText("Cancel sign-in")
		init()
	}

	override fun dispose() {
		form.disposeForm()
		super.dispose()
	}

	override fun createActions(): Array<Action> = arrayOf(cancelAction)
	override fun createCenterPanel(): JComponent = form.component
}
