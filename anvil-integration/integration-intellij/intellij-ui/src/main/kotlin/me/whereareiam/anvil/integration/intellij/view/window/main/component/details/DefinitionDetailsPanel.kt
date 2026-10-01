package me.whereareiam.anvil.integration.intellij.view.window.main.component.details

import com.intellij.util.ui.JBUI
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowPanel
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusPresentation
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import me.whereareiam.anvil.tooling.api.type.ProcessRole
import me.whereareiam.anvil.tooling.api.type.ProcessState
import me.whereareiam.anvil.tooling.api.type.SessionState
import java.awt.Rectangle
import java.util.Locale
import java.util.function.Consumer
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * Inspector for a scenario, process, or runtime-only process, declared or live.
 *
 * Repeating a request for the displayed selection is ignored, so frequent snapshot updates do not rebuild
 * the content. Definition and workspace links are delegated to the supplied callbacks.
 */
class DefinitionDetailsPanel(
	private val openDefinition: Consumer<String>,
	private val openWorkspace: Consumer<String>
) : ToolWindowPanel(DetailsLayout()), Scrollable {
	private var displayed: Selection? = null
	private var navigationStatus: DetailText? = null

	init {
		border = JBUI.Borders.empty(16)
		empty()
	}

	fun empty() {
		if (!begin(EmptySelection, "Scenario details", "Select a scenario, server, or proxy to inspect its configuration.")) return
		finish()
	}

	fun showScenario(scenario: ScenarioDescriptor) {
		if (!begin(ScenarioView(scenario, null), scenario.displayName, scenario.description)) return
		scenarioSections(scenario, null, null)
		finish()
	}

	fun showEnvironment(scenario: ScenarioDescriptor, snapshot: SessionSnapshot, state: EnvironmentState) {
		if (!begin(ScenarioView(scenario, snapshot), scenario.displayName, scenario.description)) return
		scenarioSections(scenario, snapshot, state)
		finish()
	}

	private fun scenarioSections(scenario: ScenarioDescriptor, live: SessionSnapshot?, state: EnvironmentState?) {
		val overview = section("Overview", true)
		if (live != null && state != null) {
			overview.status(
				StatusPresentation.environmentTone(state),
				StatusPresentation.environmentLabel(state),
				StatusPresentation.environmentDescription(scenario, live, state)
			)
			if (scenario.entrypoint != null) overview.fact("Processes", StatusPresentation.processSummary(scenario, live))
			overview.fact("Players", live.players.size.toString())
			val entrypoint = live.entrypoint ?: scenario.entrypoint
			if (live.state == SessionState.RUNNING || live.state == SessionState.STARTING)
				live.processes.firstOrNull { it.name == entrypoint && it.port > 0 && it.state == ProcessState.READY }
					?.let { overview.fact("Join address", "${it.host}:${it.port}") }
		}

		if (scenario.entrypoint == null) overview.fact("Definition", "Waiting for the project scenarios")
		if (scenario.entrypoint != null) {
			if (live == null) overview.fact("Processes", scenario.processes.size.toString())
			overview.fact("Entrypoint", processName(scenario, scenario.entrypoint))
			overview.fact("Mode", if (scenario.isManual) "Manual" else "Automated")
		}

		if (scenario.isSetupAvailable)
			overview.fact("Setup", if (live?.isSetupComplete == true) "Completed" else "Pending full scenario start")
		if (scenario.entrypoint != null) {
			section("Runtime").apply {
				fact("Execution", scenario.executionProviderId ?: "Project default")
				fact("Java", scenario.javaRequirement)
				fact("Startup timeout", timeout(scenario.startupTimeoutMillis))
				fact("Shutdown timeout", timeout(scenario.shutdownTimeoutMillis))
			}
		}

		section("Definition").apply {
			definition(this, scenario.definition)
			fact("Scenario ID", scenario.name)
			fact("Category", scenario.category)
			if (scenario.tags.isNotEmpty()) fact("Tags", scenario.tags.joinToString(", "))
		}
	}

	fun showProcess(scenario: ScenarioDescriptor, process: ProcessDefinition, live: ProcessSnapshot?, session: SessionState) {
		if (!begin(ProcessView(scenario, process, live, session), process.displayName, process.description)) return
		section("Overview", true).apply {
			status(
				StatusPresentation.processTone(live?.state, session),
				StatusPresentation.processLabel(live?.state, session),
				StatusPresentation.processDescription(live?.state, session)
			)
			if (live != null && live.port > 0) fact("Address", "${live.host}:${live.port}")
			fact("Role", if (process.role == ProcessRole.PROXY) "Proxy" else "Server")
			fact("Platform", process.platform.uppercase(Locale.ROOT))
			fact("Online mode", if (process.isOnlineMode) "Enabled" else "Disabled")
		}

		section("Runtime").apply {
			fact("Memory", "${process.memoryMegabytes} MiB")
			fact("Java", process.javaRequirement)
			if (live != null) workspace(this, live.workDirectory)
		}

		section("Distribution").apply {
			fact("Version", process.distributionVersion)
			fact("Build", process.distributionBuild)
			if (process.minecraftVersion != process.distributionVersion) fact("Minecraft version", process.minecraftVersion)
			fact("Selector", process.distribution)
		}

		section("Routing", true).apply {
			if (process.backendNames.isNotEmpty())
				fact("Backends", process.backendNames.joinToString(", ") { processName(scenario, it).orEmpty() })
			fact("Default backend", processName(scenario, process.defaultBackend))
		}

		section("Definition").apply {
			definition(this, scenario.definition)
			fact("Process ID", process.name)
		}

		finish()
	}

	fun showRuntimeProcess(process: ProcessSnapshot, session: SessionState) {
		if (!begin(RuntimeProcessView(process, session), process.displayName, null)) return
		section("Overview", true).apply {
			status(
				StatusPresentation.processTone(process.state, session),
				StatusPresentation.processLabel(process.state, session),
				StatusPresentation.processDescription(process.state, session)
			)
			if (process.port > 0) fact("Address", "${process.host}:${process.port}")
			fact("Process ID", process.name)
		}

		workspace(section("Runtime"), process.workDirectory)
		finish()
	}

	fun showNavigationStatus(status: String) {
		val previous = navigationStatus
		if (previous != null) {
			previous.text = status
			finish()
			return
		}

		navigationStatus = DetailText(status, TextTone.MUTED).also { add(it, true) }
		finish()
	}

	private fun definition(section: DetailSection, definitionClass: String) {
		val name = definitionClass.substringAfterLast('.').replace('$', '.')
		section.link("Definition", name, definitionClass, "Copy qualified name", openDefinition)
	}

	private fun workspace(section: DetailSection, directory: String?) {
		if (directory.isNullOrBlank()) return
		val name = directory.replace('\\', '/').trimEnd('/').substringAfterLast('/')
		section.link("Workspace", name.ifBlank { directory }, directory, "Copy path", openWorkspace)
	}

	private fun begin(identity: Selection, title: String, description: String?): Boolean {
		if (displayed == identity) return false
		displayed = identity
		navigationStatus = null
		removeAll()
		add(DetailText(title, TextTone.TITLE), true)
		if (!description.isNullOrBlank()) add(DetailText(description, TextTone.MUTED), true)
		return true
	}

	private fun section(title: String, primary: Boolean = false) =
		DetailSection(title, primary, ::labelColumn).also { add(it) }

	private fun labelColumn(): Int =
		maxOf(JBUI.scale(96), components.filterIsInstance<DetailSection>().maxOfOrNull { it.widestLabel() } ?: 0)

	private fun finish() {
		components.filterIsInstance<DetailSection>().filter { it.isEmpty() }.forEach { remove(it) }
		revalidate()
		repaint()
	}

	private fun timeout(millis: Long) = if (millis > 0) "${millis / 1000} seconds" else "Project default"

	private fun processName(scenario: ScenarioDescriptor, name: String?): String? {
		if (name.isNullOrBlank()) return null
		return scenario.processes.firstOrNull { it.name == name }?.displayName ?: name
	}

	override fun getPreferredScrollableViewportSize() = JBUI.size(720, 360)
	override fun getScrollableUnitIncrement(visible: Rectangle, orientation: Int, direction: Int) = JBUI.scale(20)
	override fun getScrollableBlockIncrement(visible: Rectangle, orientation: Int, direction: Int) =
		maxOf(JBUI.scale(20), (if (orientation == SwingConstants.VERTICAL) visible.height else visible.width) - JBUI.scale(20))
	override fun getScrollableTracksViewportWidth() = true
	override fun getScrollableTracksViewportHeight() = false

	private sealed interface Selection
	private data object EmptySelection : Selection
	private data class ScenarioView(val scenario: ScenarioDescriptor, val live: SessionSnapshot?) : Selection
	private data class ProcessView(val scenario: ScenarioDescriptor, val definition: ProcessDefinition, val live: ProcessSnapshot?, val session: SessionState) : Selection
	private data class RuntimeProcessView(val live: ProcessSnapshot, val session: SessionState) : Selection
}
