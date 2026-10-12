package me.whereareiam.anvil.integration.intellij.scenario.catalog.view

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.pom.Navigatable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import me.whereareiam.anvil.integration.intellij.scenario.ScenarioDataKeys
import me.whereareiam.anvil.integration.intellij.scenario.catalog.view.tree.ScenarioTree
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowActions
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowPanel
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowScrollPane
import me.whereareiam.anvil.integration.intellij.scenario.window.component.ToolWindowSplitter
import me.whereareiam.anvil.integration.intellij.scenario.window.component.details.DefinitionDetailsPanel
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent

/**
 * Scenario catalog screen: source selection, searchable scenario tree, details, and status messages.
 *
 * The panel owns layout and offers its selection to actions through the data context. [CatalogController] owns
 * subscriptions and decides what the screen shows; registered actions decide what commands do.
 */
class CatalogPanel(project: Project) : ToolWindowPanel(BorderLayout()), UiDataProvider, Disposable {
	@JvmField val tree = ScenarioTree()
	@JvmField val details = DefinitionDetailsPanel(
		{ controller.openDefinition(it) },
		{ controller.openWorkspace(it) }
	)
	private val sourceSelector = JComboBox<ScenarioSource>().apply {
		renderer = object : SimpleListCellRenderer<ScenarioSource>() {
			override fun customize(list: JList<out ScenarioSource>, value: ScenarioSource?, index: Int, selected: Boolean, hasFocus: Boolean) {
				text = value?.displayName ?: "Choose a scenario source"
			}
		}
		accessibleContext.accessibleName = "Scenario source"
	}
	private val sourcePicker = ToolWindowPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
		add(JBLabel("Source:"))
		add(sourceSelector)
		isVisible = false
		border = JBUI.Borders.empty(0, 8, 6, 8)
	}
	private val search = SearchTextField(false).apply { textEditor.emptyText.text = "Filter scenarios" }
	private val cards = CardLayout()
	private val content = ToolWindowPanel(cards)
	private val message = MessageCard()
	private var catalog: List<ScenarioDescriptor> = emptyList()
	private var sources: List<ScenarioSource> = emptyList()
	private var updatingSources = false
	@JvmField internal val controller: CatalogController
	private val toolbar: ActionToolbar

	init {
		controller = CatalogController(project, this)
		Disposer.register(this, controller)
		toolbar = ToolWindowActions.toolbar(PLACE, "Anvil.Catalog.Toolbar", tree)
		ToolWindowActions.popup(tree, PLACE, "Anvil.Catalog.Popup")
		EditSourceOnDoubleClickHandler.install(tree)
		add(ToolWindowPanel(BorderLayout()).apply {
			add(toolbar.component, BorderLayout.NORTH)
			add(sourcePicker, BorderLayout.SOUTH)
		}, BorderLayout.NORTH)
		val scenarios = ToolWindowPanel(BorderLayout(0, 6)).apply {
			border = JBUI.Borders.empty(0, 8, 8, 0)
			add(search, BorderLayout.NORTH)
			add(ToolWindowScrollPane(tree), BorderLayout.CENTER)
		}
		content.add(ToolWindowSplitter("Anvil.Catalog.Scenarios", 0.3f, scenarios, ToolWindowScrollPane(details)), CATALOG)
		content.add(message, MESSAGE)
		add(content, BorderLayout.CENTER)
		bindInteractions()
		controller.initialize()
	}

	override fun uiDataSnapshot(sink: DataSink) {
		val source = controller.selectedSource()
		val scenario = tree.selectedScenario()
		sink[ScenarioSource.DATA_KEY] = source
		sink[ScenarioDataKeys.SCENARIO] = scenario
		sink[ScenarioDataKeys.PROCESS] = tree.selectedProcess()?.name
		sink[ScenarioDataKeys.SESSION] = controller.activeSession()
		if (source != null && scenario != null) sink[CommonDataKeys.NAVIGATABLE] = DefinitionLink(scenario.definition)
	}

	private fun bindInteractions() {
		sourceSelector.addActionListener {
			if (!updatingSources) (sourceSelector.selectedItem as ScenarioSource?)?.let(controller::select)
		}
		search.addDocumentListener(object : DocumentAdapter() {
			override fun textChanged(event: DocumentEvent) = rebuildTree()
		})
		tree.addTreeSelectionListener { controller.selectionChanged() }
		tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), RUN)
		tree.actionMap.put(RUN, object : AbstractAction() {
			override fun actionPerformed(event: ActionEvent?) {
				val actions = ActionManager.getInstance()
				val run = actions.getAction("Anvil.Run") ?: return
				actions.tryToExecute(run, null, tree, PLACE, true)
			}
		})
	}

	fun selectedScenario(): ScenarioDescriptor? = tree.selectedScenario()

	fun selectedProcess(): ProcessDefinition? = tree.selectedProcess()

	fun setAutoExpand(expanded: Boolean) = tree.setAutoExpand(expanded)

	fun showExecution(scenario: ScenarioDescriptor?, snapshot: SessionSnapshot?, status: EnvironmentSession.Status?) =
		tree.showExecution(scenario, snapshot, status)

	fun renderSources(available: List<ScenarioSource>, selected: ScenarioSource?, enabled: Boolean) {
		updatingSources = true
		try {
			if (sources != available) {
				sources = available.toList()
				sourceSelector.removeAllItems()
				available.forEach(sourceSelector::addItem)
			}
			sourceSelector.selectedItem = selected
		} finally {
			updatingSources = false
		}
		sourcePicker.isVisible = available.size > 1
		sourceSelector.isEnabled = enabled
	}

	fun applyCatalog(scenarios: List<ScenarioDescriptor>) {
		if (catalog == scenarios) return

		catalog = scenarios.toList()
		rebuildTree()
	}

	private fun rebuildTree() {
		tree.showScenarios(catalog, search.text)
		controller.selectionChanged()
	}

	fun updateActions() = toolbar.updateActionsAsync()

	fun showMessage(title: String, body: String?, action: String?, callback: Runnable?) {
		message.show(title, body, action, callback)
		cards.show(content, MESSAGE)
	}

	fun showSecondaryAction(label: String, callback: Runnable) = message.secondary(label, callback)

	fun showCatalog() = cards.show(content, CATALOG)

	override fun dispose() {
		// The controller is a registered child and is released by the disposer.
	}

	/**
	 * Opens a scenario definition's source through the controller, for the IDE's Jump to Source and double-click.
	 */
	private inner class DefinitionLink(private val definition: String) : Navigatable {
		override fun navigate(requestFocus: Boolean) = controller.openDefinition(definition)
		override fun canNavigate() = true
		override fun canNavigateToSource() = true
	}

	private class MessageCard : ToolWindowPanel(GridBagLayout()) {
		private val title = JBLabel().apply {
			font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(15f).toFloat())
			alignmentX = CENTER_ALIGNMENT
		}
		private val body = JBLabel().apply {
			foreground = UIUtil.getContextHelpForeground()
			horizontalAlignment = JBLabel.CENTER
			alignmentX = CENTER_ALIGNMENT
		}
		private var callback: Runnable? = null
		private var secondaryCallback: Runnable? = null
		private val action = JButton().apply {
			alignmentX = CENTER_ALIGNMENT
			addActionListener { callback?.run() }
		}
		private val secondary = JButton().apply {
			alignmentX = CENTER_ALIGNMENT
			addActionListener { secondaryCallback?.run() }
		}

		init {
			add(ToolWindowPanel(null).apply {
				layout = BoxLayout(this, BoxLayout.Y_AXIS)
				border = JBUI.Borders.empty(20)
				add(title)
				add(Box.createVerticalStrut(JBUI.scale(10)))
				add(body)
				add(Box.createVerticalStrut(JBUI.scale(10)))
				add(action)
				add(Box.createVerticalStrut(JBUI.scale(6)))
				add(secondary)
			})
		}

		fun show(heading: String, explanation: String?, label: String?, callback: Runnable?) {
			title.text = heading
			val safe = explanation.orEmpty().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			body.text = "<html><div style='width:${JBUI.scale(330)}px;text-align:center'>$safe</div></html>"
			action.text = label
			action.isVisible = label != null
			this.callback = callback
			secondary.isVisible = false
			secondaryCallback = null
		}

		fun secondary(label: String, callback: Runnable) {
			secondary.text = label
			secondary.isVisible = true
			secondaryCallback = callback
		}
	}

	private companion object {
		const val PLACE = "Anvil.Catalog"
		const val RUN = "anvil.run"
		const val CATALOG = "catalog"
		const val MESSAGE = "message"
	}
}
