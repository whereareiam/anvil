package me.whereareiam.anvil.integration.intellij.scenario.environment.view.overview

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import me.whereareiam.anvil.integration.intellij.scenario.ScenarioDataKeys
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.contribution.ContributionsPanel
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowActions
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowPanel
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowScrollPane
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowSplitter
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowTree
import me.whereareiam.anvil.integration.intellij.scenario.window.component.details.DefinitionDetailsPanel
import java.awt.BorderLayout
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Environment page of a session tab: the process tree with its actions, details, and contributions.
 *
 * The panel offers its session and selection to actions through the data context; [OverviewController] owns the
 * tree model, the selection, and the details.
 */
class OverviewPanel(project: Project, private val session: EnvironmentSession) : ToolWindowPanel(BorderLayout()), UiDataProvider {
	@JvmField internal val root = DefaultMutableTreeNode("Environment")
	@JvmField internal val model = DefaultTreeModel(root)
	@JvmField internal val tree = ToolWindowTree(model).apply {
		isRootVisible = true
		showsRootHandles = true
		accessibleContext.accessibleName = "Processes"
	}
	@JvmField internal val details = DefinitionDetailsPanel({ controller.openDefinition(it) }, { controller.openWorkspace(it) })
	@JvmField internal val contributions = ContributionsPanel(session)
	private val controller: OverviewController

	init {
		val toolbar = ToolWindowActions.toolbar(PLACE, "Anvil.Environment.Toolbar", tree)
		ToolWindowActions.popup(tree, PLACE, "Anvil.Environment.Popup")
		EditSourceOnDoubleClickHandler.install(tree)
		controller = OverviewController(project, session, this, toolbar)
		val processes = ToolWindowPanel(BorderLayout()).apply {
			border = JBUI.Borders.emptyRight(8)
			add(toolbar.component, BorderLayout.NORTH)
			add(ToolWindowScrollPane(tree), BorderLayout.CENTER)
		}
		val information = ToolWindowPanel(BorderLayout()).apply {
			add(ToolWindowScrollPane(details), BorderLayout.CENTER)
			add(contributions, BorderLayout.SOUTH)
		}
		add(ToolWindowSplitter("Anvil.Environment.Processes", 0.25f, processes, information), BorderLayout.CENTER)
	}

	override fun uiDataSnapshot(sink: DataSink) {
		sink[ScenarioSource.DATA_KEY] = session.source
		sink[ScenarioDataKeys.SCENARIO] = session.scenario
		sink[ScenarioDataKeys.PROCESS] = controller.selectedProcessName()
		sink[ScenarioDataKeys.SESSION] = session
		val definition = session.scenario.definition
		sink[CommonDataKeys.NAVIGATABLE] = object : Navigatable {
			override fun navigate(requestFocus: Boolean) = controller.openDefinition(definition)
			override fun canNavigate() = true
			override fun canNavigateToSource() = true
		}
	}

	fun update() = controller.update()

	private companion object {
		const val PLACE = "Anvil.Environment"
	}
}
