package me.whereareiam.anvil.integration.intellij.view.window.account

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

internal class ProjectAccountsForm(accounts: JComponent, pools: JComponent, storage: JComponent) {
	private val feedback = JBTextArea().apply {
		isEditable = false
		isOpaque = false
		lineWrap = true
		wrapStyleWord = true
		rows = 2
		accessibleContext.accessibleName = "Account manager status"
	}
	private val messages = JBScrollPane(feedback).apply {
		border = JBUI.Borders.empty()
		isOpaque = false
		viewport.isOpaque = false
		preferredSize = JBUI.size(0, 52)
	}
	private val tabs = JBTabbedPane().apply {
		addTab("Accounts", accounts)
		addTab("Pools", pools)
	}
	val component = panel {
		row { label("Account and pool changes are saved immediately.") }
		row { cell(tabs).align(Align.FILL) }.resizableRow()
		row { cell(messages).align(AlignX.FILL) }
		row { cell(storage).align(AlignX.FILL) }
	}.apply {
		border = JBUI.Borders.empty(4)
		preferredSize = JBUI.size(740, 470)
	}

	fun showFeedback(message: String) { feedback.text = message }
}
