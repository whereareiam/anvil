package me.whereareiam.anvil.integration.intellij.view.window.main.environment.console

import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowPanel
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowScrollPane
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowSplitter
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.UIManager

/**
 * Console page of an environment tab: output source list, native console, and command input.
 * [ConsoleController] owns filtering, targeting, and the console presentation.
 */
class ConsolePanel(session: EnvironmentSession) : ToolWindowPanel(BorderLayout()) {
	@JvmField internal val sourceModel = DefaultListModel<ConsoleController.ProcessChoice>()
	@JvmField internal val sources: JBList<ConsoleController.ProcessChoice> = SourceList(sourceModel).apply {
		selectionMode = ListSelectionModel.SINGLE_SELECTION
		accessibleContext.accessibleName = "Console output sources"
	}
	@JvmField internal val target = JComboBox<ConsoleController.ProcessChoice>().apply {
		accessibleContext.accessibleName = "Command target"
	}
	@JvmField internal val command = CommandInput(session, "Console command").apply {
		accessibleContext.accessibleName = "Console command"
	}
	@JvmField internal val send = JButton("Send").apply {
		toolTipText = "Send console command to the selected target"
	}
	private val controller = ConsoleController(session, this)

	init {
		val input = ToolWindowPanel(BorderLayout(8, 0)).apply {
			border = JBUI.Borders.empty(8, 12)
			add(target, BorderLayout.WEST)
			add(command, BorderLayout.CENTER)
			add(send, BorderLayout.EAST)
		}
		val editor = ToolWindowPanel(BorderLayout()).apply {
			add(controller.presentation.console.component, BorderLayout.CENTER)
			add(input, BorderLayout.SOUTH)
		}
		add(ToolWindowSplitter(
			"Anvil.Console.Processes",
			0.2f,
			ToolWindowScrollPane(sources).apply { horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER },
			editor
		), BorderLayout.CENTER)
	}

	fun presentation() = controller.presentation
	fun update() = controller.update()

	private class SourceList(model: DefaultListModel<ConsoleController.ProcessChoice>) : JBList<ConsoleController.ProcessChoice>(model) {
		override fun getScrollableTracksViewportWidth() = true
		override fun updateUI() {
			super.updateUI()
			background = JBUI.CurrentTheme.ToolWindow.background()
			border = UIManager.getBorder("Tree.border")
			fixedCellHeight = UIManager.getInt("Tree.rowHeight").takeIf { it > 0 } ?: -1
		}
	}
}
