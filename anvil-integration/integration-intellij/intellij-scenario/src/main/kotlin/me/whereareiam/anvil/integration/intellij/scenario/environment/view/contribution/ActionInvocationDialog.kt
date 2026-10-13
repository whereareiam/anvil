package me.whereareiam.anvil.integration.intellij.scenario.environment.view.contribution

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import me.whereareiam.anvil.integration.intellij.scenario.console.CommandHistory
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionInput
import java.util.LinkedHashMap
import javax.swing.Action
import javax.swing.JComponent

/**
 * Action dialog presentation; invocation, history, and availability remain in Java.
 */
class ActionInvocationDialog(
	private val session: EnvironmentSession,
	private val action: ActionDescriptor
) : DialogWrapper(session.ideProject) {
	private val form: ActionInvocationForm
	private val controller: ActionInvocationController

	init {
		val history = session.ideProject.getService(CommandHistory::class.java)
		val histories = LinkedHashMap<String, List<String>>()
		for (input in action.definition.inputs) {
			if (!input.isSensitive) histories[input.name] = history.history(key(input))
		}
		form = ActionInvocationForm(
			action,
			"${ContributionsPanel.kind(action.target)}: ${action.target.name}",
			session.actionDraft(action),
			histories,
			history.limit()
		)
		controller = ActionInvocationController(
			session,
			action,
			form,
			{ enabled -> setOKActionEnabled(enabled) },
			{ message -> setErrorText(message) },
			{ isDisposed }
		)
		title = ContributionsPanel.label(action.definition)
		setOKButtonText("Run")
		setCancelButtonText("Close")
		init()
	}

	public override fun createCenterPanel(): JComponent = controller.component()
	public override fun doValidate(): ValidationInfo? = controller.validate()
	public override fun doOKAction() = controller.submit()
	override fun dispose() {
		controller.close()
		super.dispose()
	}

	private fun key(input: ActionInput) = CommandHistory.Scope.builder()
		.source(session.source.id)
		.definition(session.scenario.definition)
		.target("${action.target.type}:${action.target.name}")
		.operation("${action.definition.id}:${input.name}")
		.build()
}
