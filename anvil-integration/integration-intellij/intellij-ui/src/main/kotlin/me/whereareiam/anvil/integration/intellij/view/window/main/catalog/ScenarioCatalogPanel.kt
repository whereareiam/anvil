package me.whereareiam.anvil.integration.intellij.view.window.main.catalog

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState
import me.whereareiam.anvil.integration.intellij.view.window.main.catalog.tree.ScenarioTreeView
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowActions
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowPanel
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowScrollPane
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowSplitter
import me.whereareiam.anvil.integration.intellij.view.window.main.component.details.DefinitionDetailsPanel
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
import java.util.function.BiConsumer
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
 * The panel owns layout and display state. [CatalogController] is created after every component exists
 * and owns subscriptions, command availability, and what the screen shows.
 */
class ScenarioCatalogPanel(
	project: Project,
	starter: BiConsumer<ScenarioSource, ScenarioDescriptor>,
	processStarter: ProcessStarter
) : ToolWindowPanel(BorderLayout(0, 6)), CatalogView, Disposable {
	private val tree = ScenarioTreeView()
	private val detailsPanel = DefinitionDetailsPanel(
		{ controller.commands().openDefinition(it) },
		{ controller.commands().openWorkspace(it) }
	)
	private val sourceSelector = JComboBox<ScenarioSource>().apply {
		renderer = object : SimpleListCellRenderer<ScenarioSource>() {
			override fun customize(
				list: JList<out ScenarioSource>,
				value: ScenarioSource?,
				index: Int,
				selected: Boolean,
				hasFocus: Boolean
			) {
				text = value?.displayName ?: "Choose a scenario source"
			}
		}
	}
	private val sourcePicker = ToolWindowPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
		add(JBLabel("Scenario source:"))
		add(sourceSelector)
		isVisible = false
		border = JBUI.Borders.empty(0, 8, 6, 8)
	}
	private val search = SearchTextField().apply { textEditor.emptyText.text = "Search scenarios" }
	private val cards = CardLayout()
	private val content = ToolWindowPanel(cards)
	private val message = MessageCard()
	private var catalog: List<ScenarioDescriptor> = emptyList()
	private var sources: List<ScenarioSource> = emptyList()
	private var updatingSources = false

	@JvmField internal val controller: CatalogController
	private val toolbar: ActionToolbar

	init {
		controller = CatalogController(project, this, starter, processStarter)
		Disposer.register(this, controller)
		toolbar = createToolbar(controller.discovery(), controller.commands())
		add(ToolWindowPanel(BorderLayout()).apply {
			add(toolbar.component, BorderLayout.NORTH)
			add(sourcePicker, BorderLayout.SOUTH)
		}, BorderLayout.NORTH)
		val catalogPanel = ToolWindowPanel(BorderLayout(0, 8)).apply {
			border = JBUI.Borders.empty(8)
			add(search, BorderLayout.NORTH)
			add(ToolWindowScrollPane(tree), BorderLayout.CENTER)
		}
		content.add(ToolWindowSplitter("Anvil.Catalog.Scenarios", 0.28f, catalogPanel, ToolWindowScrollPane(detailsPanel)), "catalog")
		content.add(message, "message")
		add(content, BorderLayout.CENTER)
		bindInteractions()
		controller.discovery().initialize()
	}

	private fun createToolbar(discovery: CatalogDiscoveryController, commands: CatalogCommandController) =
		ToolWindowActions.toolbar(
			"Anvil.Catalog", this,
			ToolWindowActions.action(discovery::refreshLabel, AllIcons.Actions.Refresh, discovery::canRefresh, discovery::refresh),
			ToolWindowActions.action("Sync project", AllIcons.Actions.SyncPanels, discovery::canSync, discovery::sync),
			ToolWindowActions.action("Accounts…", AllIcons.General.User, { true }, commands::accounts),
			Separator.getInstance(),
			ToolWindowActions.action(commands::startLabel, AllIcons.Actions.Execute, commands::canStart, commands::startSelected),
			ToolWindowActions.action("Save run configuration", AllIcons.Actions.MenuSaveall, commands::hasSelection, commands::saveSelected),
			Separator.getInstance(),
			ToolWindowActions.action("Open source", AllIcons.Actions.EditSource, commands::hasSelection, commands::openSource)
		)

	private fun bindInteractions() {
		val execution = controller.execution()
		sourceSelector.addActionListener {
			if (!updatingSources) (sourceSelector.selectedItem as ScenarioSource?)?.let(controller.discovery()::select)
		}
		search.addDocumentListener(object : DocumentAdapter() {
			override fun textChanged(event: DocumentEvent) = rebuildTree()
		})
		tree.addTreeSelectionListener { execution.selectionChanged() }
		tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "anvil.run.scenario")
		tree.actionMap.put("anvil.run.scenario", object : AbstractAction() {
			override fun actionPerformed(event: ActionEvent?) {
				if (tree.isEnvironmentSelection()) controller.commands().startSelected()
			}
		})
	}

	override fun selectedScenario(): ScenarioDescriptor? = tree.selectedScenario()

	override fun selectedProcess(): ProcessDefinition? = tree.selectedProcess()

	override fun setAutoExpand(expanded: Boolean) = tree.setAutoExpand(expanded)

	override fun showExecution(scenario: ScenarioDescriptor?, snapshot: SessionSnapshot?, state: EnvironmentState?) =
		tree.showExecution(scenario, snapshot, state)

	override fun details(): DefinitionDetailsPanel = detailsPanel

	override fun renderSources(available: List<ScenarioSource>, selected: ScenarioSource?, enabled: Boolean) {
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

	override fun applyCatalog(scenarios: List<ScenarioDescriptor>) {
		if (catalog == scenarios) return

		catalog = scenarios.toList()
		rebuildTree()

		if (catalog.isNotEmpty()) showCatalog()
	}

	private fun rebuildTree() {
		tree.showScenarios(catalog, search.text)
		controller.execution().selectionChanged()
	}

	override fun updateActions() {
		toolbar.updateActionsAsync()
	}

	override fun showMessage(title: String, body: String?, action: String?, callback: Runnable?) {
		message.show(title, body, action, callback)
		cards.show(content, "message")
	}

	override fun showSyncFailure(details: String, inspect: Runnable, retry: Runnable) {
		showMessage("Project sync needs attention", details, "View sync details", inspect)
		message.secondary("Retry sync", retry)
	}

	override fun showCatalog() = cards.show(content, "catalog")

	override fun dispose() {
		// The controller is a registered child and is released by the disposer.
	}

	/**
	 * Starts one declared process of a scenario, reusing a matching active environment when present.
	 */
	fun interface ProcessStarter {
		fun start(source: ScenarioSource, scenario: ScenarioDescriptor, processName: String)
	}

	private class MessageCard : ToolWindowPanel(GridBagLayout()) {
		private val title = JBLabel().apply {
			font = font.deriveFont(Font.BOLD, 16f)
			alignmentX = CENTER_ALIGNMENT
		}
		private val body = JBLabel().apply {
			isOpaque = false
			font = UIUtil.getLabelFont()
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
				add(Box.createVerticalStrut(10))
				add(body)
				add(Box.createVerticalStrut(10))
				add(action)
				add(Box.createVerticalStrut(6))
				add(secondary)
			})
		}

		fun show(heading: String, explanation: String?, label: String?, callback: Runnable?) {
			title.text = heading
			val safe = explanation.orEmpty().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			body.text = "<html><div style='width:330px;text-align:center'>$safe</div></html>"
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
}
