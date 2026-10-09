package me.whereareiam.anvil.integration.intellij.view.window.main

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import me.whereareiam.anvil.tooling.api.type.ProcessRole
import me.whereareiam.anvil.tooling.api.type.ProcessState
import java.util.Locale
import javax.swing.Icon

/**
 * Shared scenario identity, topology, and command rules for the tool-window screens.
 *
 * The catalog, environment tabs, and console apply these rules so that each screen reaches the same
 * conclusion about which run a scenario belongs to and which commands it accepts.
 */
object ScenarioPresentation {
	private val STARTABLE_STATES = setOf(ProcessState.CREATED, ProcessState.STOPPED, ProcessState.FAILED)

	/**
	 * Returns the icon for a scenario shown as a group of processes.
	 */
	@JvmStatic fun environmentIcon(): Icon = AllIcons.Nodes.Folder

	/**
	 * Returns the server or proxy icon for a declared process.
	 */
	@JvmStatic fun processIcon(process: ProcessDefinition): Icon = processIcon(process.role)

	/**
	 * Returns the server or proxy icon for a process role.
	 */
	@JvmStatic fun processIcon(role: ProcessRole): Icon = IconLoader.getIcon(
		if (role == ProcessRole.PROXY) "/icons/proxy.svg" else "/icons/server.svg",
		ScenarioPresentation::class.java
	)

	/**
	 * Returns the platform with its distribution version, or its build when the version repeats the platform.
	 */
	@JvmStatic fun platformLabel(process: ProcessDefinition): String {
		val platform = process.platform.uppercase(Locale.ROOT)
		val version = process.distributionVersion ?: return platform
		if (version.equals(process.platform, ignoreCase = true))
			return platform + (process.distributionBuild?.let { " (build $it)" } ?: "")
		return "$platform $version"
	}

	/**
	 * Returns the stable identity of a scenario within one prepared source.
	 *
	 * @param scenario discovered or saved scenario
	 * @return the definition class and scenario name, independent of presentation metadata
	 */
	@JvmStatic fun identity(scenario: ScenarioDescriptor): String = "${scenario.definition}/${scenario.name}"

	/**
	 * Reports whether two descriptors identify the same scenario, ignoring enriched metadata.
	 */
	@JvmStatic fun sameScenario(first: ScenarioDescriptor, second: ScenarioDescriptor): Boolean =
		first.definition == second.definition && first.name == second.name

	/**
	 * Reports whether a session executes the given scenario from the given source.
	 */
	@JvmStatic fun runs(session: EnvironmentSession, source: ScenarioSource, scenario: ScenarioDescriptor): Boolean =
		session.source.id == source.id && sameScenario(session.scenario, scenario)

	/**
	 * Returns the only process of a scenario that is presented as that process rather than as a group.
	 *
	 * @return the process, or null when the scenario has several processes or a setup phase
	 */
	@JvmStatic fun standaloneProcess(scenario: ScenarioDescriptor): ProcessDefinition? =
		scenario.processes.singleOrNull()?.takeUnless { scenario.isSetupAvailable }

	/**
	 * Merges declared processes with live snapshots in declaration order, followed by undeclared live processes.
	 */
	@JvmStatic fun processes(scenario: ScenarioDescriptor, snapshot: SessionSnapshot): List<ScenarioProcess> {
		val merged = LinkedHashMap<String, ScenarioProcess>()
		for (definition in scenario.processes)
			merged[definition.name] = ScenarioProcess(definition.name, definition.displayName, definition, null)
		for (live in snapshot.processes)
			merged[live.name] = ScenarioProcess(live.name, live.displayName, merged[live.name]?.definition, live)

		return merged.values.toList()
	}

	/**
	 * Returns the latest snapshot of one process, or null when the process has not been created.
	 */
	@JvmStatic fun liveProcess(snapshot: SessionSnapshot, name: String?): ProcessSnapshot? =
		name?.let { snapshot.processes.firstOrNull { process -> process.name == it } }

	/**
	 * Reports whether the full scenario start has remaining work: setup or a process that is not ready.
	 */
	@JvmStatic fun canStartScenario(scenario: ScenarioDescriptor, snapshot: SessionSnapshot): Boolean =
		!snapshot.isSetupComplete || scenario.processes.any { definition ->
			snapshot.processes.none { process -> process.name == definition.name && process.state == ProcessState.READY }
		}

	/**
	 * Reports whether a process can be started from its current live state.
	 *
	 * @param live latest process snapshot, or null when the process has not been created yet
	 */
	@JvmStatic fun canStartProcess(live: ProcessSnapshot?): Boolean = live == null || live.state in STARTABLE_STATES

	/**
	 * Returns the start command label for one process role.
	 */
	@JvmStatic fun startProcessLabel(role: ProcessRole?): String =
		if (role == ProcessRole.PROXY) "Start proxy" else "Start server"

	/**
	 * One process of an environment, combining its declaration with its latest live snapshot.
	 *
	 * @property definition declaration, or null for a live process the loaded scenario does not declare
	 * @property live latest snapshot, or null before the process is created
	 */
	data class ScenarioProcess(
		val name: String,
		val displayName: String,
		val definition: ProcessDefinition?,
		val live: ProcessSnapshot?
	)
}
