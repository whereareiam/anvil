package me.whereareiam.anvil.integration.intellij.account.view

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

/**
 * Account window layout: the accounts and pools tabs, a status line, and a link to the account directories.
 */
internal class AccountsForm(accounts: JComponent, pools: JComponent, openDirectories: Runnable) {
	private val feedback = JBLabel().apply {
		foreground = UIUtil.getContextHelpForeground()
		setCopyable(true)
		accessibleContext.accessibleName = "Account manager status"
	}
	private val tabs = JBTabbedPane().apply {
		addTab("Accounts", accounts)
		addTab("Pools", pools)
	}
	val component = panel {
		row { cell(tabs).align(Align.FILL) }.resizableRow()
		row { cell(feedback).align(AlignX.FILL) }
		row {
			comment("Changes are saved immediately.")
			link("Account directories…") { openDirectories.run() }.align(AlignX.RIGHT)
		}
	}.apply {
		border = JBUI.Borders.empty(4)
		preferredSize = JBUI.size(740, 470)
	}

	fun showFeedback(message: String) {
		feedback.text = if (message.isEmpty()) "" else "<html>${message.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>")}</html>"
	}
}
