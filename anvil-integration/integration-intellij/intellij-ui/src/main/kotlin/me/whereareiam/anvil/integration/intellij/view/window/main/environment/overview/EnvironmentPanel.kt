package me.whereareiam.anvil.integration.intellij.view.window.main.environment.overview

import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowPanel
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowScrollPane
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowSplitter
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowTree
import me.whereareiam.anvil.integration.intellij.view.window.main.component.details.DefinitionDetailsPanel
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.action.TargetContributionsPanel
import java.awt.BorderLayout
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Environment page of a session tab: process tree with lifecycle commands, details, and contributions.
 * [EnvironmentController] owns the tree model, selection, and commands.
 */
class EnvironmentPanel(project: Project, session: EnvironmentSession) : ToolWindowPanel(BorderLayout()) {
	@JvmField internal val root = DefaultMutableTreeNode("Environment")
	@JvmField internal val model = DefaultTreeModel(root)
	@JvmField internal val tree = ToolWindowTree(model).apply {
		isRootVisible = true
		showsRootHandles = true
	}
	@JvmField internal val details = DefinitionDetailsPanel({ controller.openDefinition(it) }, { controller.openWorkspace(it) })
	@JvmField internal val contributions = TargetContributionsPanel(session)
	private val controller: EnvironmentController

	init {
		controller = EnvironmentController(project, session, this)
		val processes = ToolWindowPanel(BorderLayout()).apply {
			border = JBUI.Borders.empty(0, 0, 0, 8)
			add(controller.toolbar.component, BorderLayout.NORTH)
			add(ToolWindowScrollPane(tree), BorderLayout.CENTER)
		}
		val information = ToolWindowPanel(BorderLayout()).apply {
			add(ToolWindowScrollPane(details), BorderLayout.CENTER)
			add(contributions, BorderLayout.SOUTH)
		}
		add(ToolWindowSplitter("Anvil.Environment.Processes", 0.25f, processes, information), BorderLayout.CENTER)
	}

	fun update() = controller.update()
}
