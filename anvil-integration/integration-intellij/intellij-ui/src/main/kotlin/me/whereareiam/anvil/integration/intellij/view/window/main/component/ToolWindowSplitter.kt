package me.whereareiam.anvil.integration.intellij.view.window.main.component

import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

/**
 * Horizontal sidebar and content split used by the tool-window screens.
 *
 * Both sides keep a readable minimum width, so a narrow tool window shrinks the proportion instead of
 * truncating sidebar names. A proportion key remembers the user's divider position across sessions.
 */
class ToolWindowSplitter(
	proportionKey: String,
	proportion: Float,
	sidebar: JComponent,
	content: JComponent
) : OnePixelSplitter(false, proportionKey, proportion) {
	init {
		sidebar.minimumSize = JBUI.size(SIDEBAR_MINIMUM, 0)
		content.minimumSize = JBUI.size(CONTENT_MINIMUM, 0)
		setHonorComponentsMinimumSize(true)
		firstComponent = sidebar
		secondComponent = content
	}

	private companion object {
		const val SIDEBAR_MINIMUM = 160
		const val CONTENT_MINIMUM = 200
	}
}
