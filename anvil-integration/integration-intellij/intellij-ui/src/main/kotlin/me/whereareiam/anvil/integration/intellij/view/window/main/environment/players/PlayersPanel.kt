package me.whereareiam.anvil.integration.intellij.view.window.main.environment.players

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowList
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowPanel
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowScrollPane
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowSplitter
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.action.TargetContributionsPanel
import me.whereareiam.anvil.tooling.api.model.PlayerDescriptor
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

/**
 * Players page of a session tab: retained players beside the selected player's status, actions, and
 * observations. [PlayersController] retains observed players and binds their contributions.
 */
class PlayersPanel(session: EnvironmentSession) : ToolWindowPanel(BorderLayout()) {
	@JvmField internal val model = DefaultListModel<PlayerDescriptor>()
	@JvmField internal val players = ToolWindowList(model).apply {
		selectionMode = ListSelectionModel.SINGLE_SELECTION
		emptyText.text = "Players appear when the scenario creates them."
		accessibleContext.accessibleName = "Players"
	}
	@JvmField internal val title = JBLabel().apply {
		font = UIUtil.getLabelFont().deriveFont(Font.BOLD, UIUtil.getLabelFont().size2D + 2)
	}
	@JvmField internal val info = JBLabel().apply {
		foreground = UIUtil.getContextHelpForeground()
	}
	@JvmField internal val contributions = TargetContributionsPanel(session)
	private val controller: PlayersController

	init {
		val header = ToolWindowPanel(BorderLayout(0, 4)).apply {
			border = JBUI.Borders.empty(12, 12, 4, 12)
			add(title, BorderLayout.NORTH)
			add(info, BorderLayout.CENTER)
		}
		val details = ToolWindowPanel(BorderLayout()).apply {
			add(header, BorderLayout.NORTH)
			add(contributions, BorderLayout.CENTER)
		}
		val sidebar = ToolWindowScrollPane(players).apply {
			horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
		}
		add(ToolWindowSplitter("Anvil.Players", 0.25f, sidebar, details), BorderLayout.CENTER)
		controller = PlayersController(session, this)
	}

	fun update() = controller.update()
}
