package me.whereareiam.anvil.integration.intellij.view.window.main.component

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ui.JBUI
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Builds tool-window toolbars from Java suppliers, so controllers keep labels and availability rules.
 */
object ToolWindowActions {
	/**
	 * Creates an action with a fixed label.
	 */
	@JvmStatic
	fun action(text: String, icon: Icon, enabled: BooleanSupplier, execute: Runnable): AnAction =
		action({ text }, icon, enabled, execute)

	/**
	 * Creates an action whose label and availability are recomputed on every update.
	 * The availability is checked again before execution.
	 */
	@JvmStatic
	fun action(text: Supplier<String>, icon: Icon, enabled: BooleanSupplier, execute: Runnable): AnAction =
		object : DumbAwareAction(text.get(), text.get(), icon) {
			override fun actionPerformed(event: AnActionEvent) {
				if (enabled.asBoolean) execute.run()
			}
			override fun update(event: AnActionEvent) {
				event.presentation.text = text.get()
				event.presentation.description = text.get()
				event.presentation.isEnabled = enabled.asBoolean
			}
			override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
		}

	/**
	 * Creates a transparent horizontal toolbar whose actions use the given data context component.
	 */
	@JvmStatic
	fun toolbar(place: String, target: JComponent, vararg actions: AnAction): ActionToolbar {
		val toolbar = ActionManager.getInstance().createActionToolbar(place, DefaultActionGroup(*actions), true)
		toolbar.targetComponent = target
		toolbar.component.isOpaque = false
		toolbar.component.border = JBUI.Borders.empty(2, 8)

		return toolbar
	}
}
