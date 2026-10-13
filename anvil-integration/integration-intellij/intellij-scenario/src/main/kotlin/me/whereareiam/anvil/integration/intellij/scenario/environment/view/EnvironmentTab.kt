package me.whereareiam.anvil.integration.intellij.scenario.environment.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.console.ConsolePanel
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.overview.OverviewPanel
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.players.PlayersPanel
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowPanel
import me.whereareiam.anvil.integration.intellij.scenario.window.component.status.StatusBadge
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tool-window tab for one environment session: status badge, section tabs, and the environment, console,
 * and players pages. [EnvironmentTabController] owns the session subscription and disposal.
 */
class EnvironmentTab(project: Project, session: EnvironmentSession) : ToolWindowPanel(BorderLayout()), Disposable {
	@JvmField internal val status = StatusBadge()
	@JvmField internal val environment = OverviewPanel(project, session)
	@JvmField internal val console = ConsolePanel(session)
	@JvmField internal val players = PlayersPanel(session)
	private val tabs = SectionTabs()
	private val pages = CardLayout()
	private val content = ToolWindowPanel(pages)
	private val controller: EnvironmentTabController

	init {
		addSection("Environment", environment)
		addSection("Console", console)
		addSection("Players", players)
		val leading = JPanel(GridBagLayout()).apply {
			isOpaque = false
			border = JBUI.Borders.empty(0, 12, 0, 4)
			add(status)
		}
		tabs.isOpaque = false
		val header = object : ToolWindowPanel(BorderLayout(HEADER_GAP, 0)) {
			override fun doLayout() {
				// The badge gives up its label before any section tab is clipped.
				val available = width - insets.left - insets.right - leading.insets.left - leading.insets.right -
					HEADER_GAP - tabs.runWidth()
				status.compact = available < status.fullWidth()
				super.doLayout()
			}
		}
		add(header.apply {
			border = JBUI.Borders.customLineBottom(JBColor.border())
			add(leading, BorderLayout.WEST)
			add(tabs, BorderLayout.CENTER)
		}, BorderLayout.NORTH)
		add(content, BorderLayout.CENTER)
		controller = EnvironmentTabController(project, session, this)
	}

	private fun addSection(name: String, page: JComponent) {
		content.add(page, name)
		page.accessibleContext.accessibleName = name
		tabs.addTab(name, JPanel(null).apply {
			isOpaque = false
			preferredSize = Dimension()
		})
	}

	fun showSection(index: Int) { pages.show(content, tabs.getTitleAt(index)) }
	fun tabs(): SectionTabs = tabs
	fun consolePresentation() = console.presentation()
	fun revealConsole() = controller.revealConsole()
	override fun dispose() = controller.dispose()

	private companion object {
		const val HEADER_GAP = 8
	}
}
