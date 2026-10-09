package me.whereareiam.anvil.integration.intellij.view.window.main.component

import com.intellij.util.ui.JBUI
import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * A panel that follows the tool-window background after theme changes.
 */
open class ToolWindowPanel(layout: LayoutManager?) : JPanel(layout) {
	override fun updateUI() {
		super.updateUI()
		background = JBUI.CurrentTheme.ToolWindow.background()
	}
}
