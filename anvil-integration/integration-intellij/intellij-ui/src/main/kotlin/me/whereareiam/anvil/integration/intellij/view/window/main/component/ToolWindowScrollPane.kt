package me.whereareiam.anvil.integration.intellij.view.window.main.component

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

/**
 * Borderless scrolling content that follows the tool-window theme.
 */
class ToolWindowScrollPane(content: JComponent) : JBScrollPane(content) {
	override fun updateUI() {
		super.updateUI()
		border = JBUI.Borders.empty()
		viewportBorder = null
		background = JBUI.CurrentTheme.ToolWindow.background()
		viewport?.background = background
	}
}
