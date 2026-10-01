package me.whereareiam.anvil.integration.intellij.view.window.main.environment.action

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionInput
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextArea
import javax.swing.UIManager
import javax.swing.table.DefaultTableModel

/**
 * Input widgets and result presentation; session operations remain in the Java dialog.
 */
internal class ActionInvocationForm(
	action: ActionDescriptor,
	targetLabel: String,
	draft: Map<String, String>,
	histories: Map<String, List<String>>,
	historyLimit: Int
) {
	private val inputs = linkedMapOf<ActionInput, JComponent>()
	private val resultMessage = JTextArea().apply {
		isEditable = false
		isOpaque = false
		lineWrap = true
		wrapStyleWord = true
		font = UIManager.getFont("Label.font")
	}
	private val resultModel = object : DefaultTableModel() {
		override fun isCellEditable(row: Int, column: Int): Boolean = false
	}
	private val resultTable = JBScrollPane(JBTable(resultModel)).apply {
		preferredSize = JBUI.size(480, 170)
	}
	private lateinit var resultRow: Row

	val component = panel {
		row { label(targetLabel) }
		action.definition.description?.let { row { label(it) } }
		for (input in action.definition.inputs) {
			val widget = createInput(input, draft, histories, historyLimit)
			inputs[input] = widget
			val label = input.displayName ?: input.name
			widget.accessibleContext.accessibleName = label
			row(label) { cell(widget).align(AlignX.FILL) }
		}
		row { cell(resultMessage).align(AlignX.FILL) }
		resultRow = row { cell(resultTable).align(AlignX.FILL) }.visible(false)
	}.apply { minimumSize = JBUI.size(500, 120) }

	private fun createInput(
		input: ActionInput,
		draft: Map<String, String>,
		histories: Map<String, List<String>>,
		historyLimit: Int
	): JComponent {
		val value = draft[input.name] ?: input.defaultValue.orEmpty()
		if (input.isSensitive) return JPasswordField(value, 28)
		if (input.type == ActionInputType.BOOLEAN || input.type == ActionInputType.CHOICE) {
			val choices = if (input.type == ActionInputType.BOOLEAN) listOf("false", "true") else input.choices
			return JComboBox((listOf("") + choices).toTypedArray()).apply { selectedItem = value }
		}
		return TextFieldWithHistory().apply {
			setHistorySize(historyLimit)
			history = histories[input.name].orEmpty()
			text = value
		}
	}

	private fun value(input: ActionInput, component: JComponent): String? {
		val value = when (component) {
			is JPasswordField -> String(component.password)
			is TextFieldWithHistory -> component.text
			else -> (component as JComboBox<*>).selectedItem as String?
		}
		if (input.type != ActionInputType.TEXT && value.isNullOrBlank()) return null
		return value
	}

	fun validateInputs(): ValidationInfo? {
		for ((input, component) in inputs) {
			try {
				input.validate(value(input, component))
			} catch (failure: IllegalArgumentException) {
				return ValidationInfo(failure.message.orEmpty(), component)
			}
		}
		return null
	}

	fun arguments(): Map<String, String> = buildMap {
		for ((input, component) in inputs)
			value(input, component)?.let { put(input.name, it) }
	}

	fun draft(): Map<String, String> = buildMap {
		for ((input, component) in inputs) {
			if (input.isSensitive) continue
			value(input, component)?.let { put(input.name, it) }
		}
	}

	fun showResult(result: ActionResult) {
		resultMessage.text = result.message ?: if (result.isSuccessful) "Completed" else "Action failed"
		resultModel.setDataVector(result.rows.map { it.toTypedArray() }.toTypedArray(), result.columns.toTypedArray())
		resultRow.visible(result.columns.isNotEmpty())
		component.revalidate()
	}
}
