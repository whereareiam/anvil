package me.whereareiam.anvil.integration.intellij.view.window.main.component

import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import javax.swing.tree.TreeModel

/**
 * A tree that follows the tool-window background after theme changes.
 */
open class ToolWindowTree(model: TreeModel) : Tree(model) {
	override fun updateUI() {
		super.updateUI()
		background = JBUI.CurrentTheme.ToolWindow.background()
	}
}
