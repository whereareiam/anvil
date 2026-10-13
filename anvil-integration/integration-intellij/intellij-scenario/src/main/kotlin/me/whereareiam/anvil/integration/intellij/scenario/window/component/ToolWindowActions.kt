package me.whereareiam.anvil.integration.intellij.scenario.window.component

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

/**
 * Shows registered action groups in tool-window toolbars and context menus.
 *
 * Actions read what they apply to from the target component's data context, so a view only offers its
 * selection; it never decides what a command does or whether it is available.
 */
object ToolWindowActions {
	/**
	 * Creates a transparent horizontal toolbar for a registered group whose actions use the target's data context.
	 */
	@JvmStatic
	fun toolbar(place: String, groupId: String, target: JComponent): ActionToolbar {
		val toolbar = ActionManager.getInstance().createActionToolbar(place, group(groupId), true)
		toolbar.targetComponent = target
		toolbar.component.isOpaque = false
		toolbar.component.border = JBUI.Borders.empty(2, 8)

		return toolbar
	}

	/**
	 * Shows a registered group as the component's context menu.
	 */
	@JvmStatic
	fun popup(component: JComponent, place: String, groupId: String) {
		PopupHandler.installPopupMenu(component, group(groupId), place)
	}

	private fun group(id: String): ActionGroup = ActionManager.getInstance().getAction(id) as ActionGroup
}
