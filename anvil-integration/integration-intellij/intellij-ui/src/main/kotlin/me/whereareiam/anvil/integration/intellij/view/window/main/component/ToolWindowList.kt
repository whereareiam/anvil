package me.whereareiam.anvil.integration.intellij.view.window.main.component

import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import javax.swing.ListModel

/**
 * A list that follows the tool-window theme.
 */
class ToolWindowList<E>(model: ListModel<E>) : JBList<E>(model) {
	override fun updateUI() {
		super.updateUI()
		background = JBUI.CurrentTheme.ToolWindow.background()
	}
}
