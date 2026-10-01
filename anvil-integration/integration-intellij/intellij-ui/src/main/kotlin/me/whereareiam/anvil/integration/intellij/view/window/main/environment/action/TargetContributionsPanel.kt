package me.whereareiam.anvil.integration.intellij.view.window.main.environment.action

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowList
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowPanel
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDescriptor
import me.whereareiam.anvil.tooling.api.type.ObservationTone
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList

/**
 * Contributed actions and observations for the selected scenario, process, or player target.
 */
class TargetContributionsPanel(session: EnvironmentSession) : ToolWindowPanel(BorderLayout(8, 8)) {
	var multipleScopes = false
	@JvmField internal val actions = JComboBox<ActionDescriptor>().apply {
		renderer = textListCellRenderer {
			label(it.definition) + if (multipleScopes) " · ${kind(it.target)}" else ""
		}
	}
	@JvmField internal val invoke = JButton("Open action…")
	@JvmField internal val availability = JBLabel()
	@JvmField internal val observations = DefaultListModel<ObservationDescriptor>()
	private val actionControls = ToolWindowPanel(BorderLayout(8, 0)).apply {
		add(actions, BorderLayout.CENTER)
		add(invoke, BorderLayout.EAST)
		isVisible = false
	}
	private val controller = TargetContributionsController(session, this)

	init {
		border = JBUI.Borders.empty(8, 12)
		isVisible = false
		add(actionControls, BorderLayout.NORTH)
		add(ToolWindowList(observations).apply {
			cellRenderer = ObservationRenderer()
			emptyText.text = "No observations for this target"
		}, BorderLayout.CENTER)
		add(availability, BorderLayout.SOUTH)
	}

	fun update(target: ActionTarget?, vararg additional: ActionTarget) = controller.update(target, *additional)

	fun setActionsVisible(visible: Boolean) {
		actions.parent?.isVisible = visible
	}

	private class ObservationRenderer : ColoredListCellRenderer<ObservationDescriptor>() {
		override fun customizeCellRenderer(list: JList<out ObservationDescriptor>, value: ObservationDescriptor?, index: Int, selected: Boolean, focus: Boolean) {
			if (value == null) return
			append("${value.definition.displayName ?: value.definition.id}: ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
			val color = when (value.value.tone) {
				ObservationTone.INFO -> null
				ObservationTone.SUCCESS -> JBColor(0x28733c, 0x73bd8b)
				ObservationTone.WARNING -> JBColor(0x966000, 0xe6ad4c)
				ObservationTone.ERROR -> JBColor.RED
			}
			append(value.value.text, if (selected || color == null) SimpleTextAttributes.REGULAR_ATTRIBUTES
				else SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))
		}
	}

	companion object {
		@JvmStatic fun kind(target: ActionTarget): String = when (target.type) {
			ActionTargetType.SCENARIO -> "Scenario"
			ActionTargetType.PROCESS -> "Process"
			ActionTargetType.PLAYER -> "Player"
		}
		@JvmStatic fun label(definition: ActionDefinition): String = definition.displayName ?: definition.id
	}
}
