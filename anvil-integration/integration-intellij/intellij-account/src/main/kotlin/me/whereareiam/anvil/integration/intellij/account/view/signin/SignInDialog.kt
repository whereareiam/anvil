package me.whereareiam.anvil.integration.intellij.account.view.signin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import me.whereareiam.anvil.integration.intellij.account.AccountSignIn
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.Action
import javax.swing.JComponent

/**
 * Presents one browser sign-in while [SignInController] owns the asynchronous operation.
 */
class SignInDialog(
	project: Project,
	accountId: String,
	factory: Function<Consumer<String>, out AccountSignIn>,
	changed: Runnable
) : DialogWrapper(project, false) {
	private val form = SignInForm()
	private val controller: SignInController

	constructor(project: Project, source: ScenarioSource, accountId: String, changed: Runnable) : this(
		project,
		accountId,
		Function { output -> AccountSignIn.forProject(project, source, accountId, output) },
		changed
	)

	init {
		controller = SignInController(project, accountId, factory, form, changed) { setCancelButtonText("Close") }
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
