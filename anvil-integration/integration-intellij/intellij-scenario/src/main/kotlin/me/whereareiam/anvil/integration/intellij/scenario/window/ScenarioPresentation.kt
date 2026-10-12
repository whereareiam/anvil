package me.whereareiam.anvil.integration.intellij.scenario.window

import com.intellij.openapi.util.IconLoader
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor
import me.whereareiam.anvil.tooling.api.type.ProcessRole
import java.util.Locale
import javax.swing.Icon

/**
 * How the tool-window screens show scenarios and processes: icons, labels, identities, and topology.
 *
 * The catalog, environment tabs, and console use these so that a scenario looks the same everywhere. Which
 * commands a scenario accepts is decided by the environment session, not here.
 */
object ScenarioPresentation {
	/**
	 * Returns the icon of a scenario shown as a group of processes.
	 */
	@JvmStatic fun scenarioIcon(): Icon = IconLoader.getIcon("/icons/anvil.svg", ScenarioPresentation::class.java)

	/**
	 * Returns the server or proxy icon of a declared process.
	 */
	@JvmStatic fun processIcon(process: ProcessDefinition): Icon = processIcon(process.role)

	/**
	 * Returns the server or proxy icon of a process role.
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
	 * Returns the stable identity of a scenario within one source: its definition class, which declares exactly
	 * one scenario.
	 */
	@JvmStatic fun identity(scenario: ScenarioDescriptor): String = scenario.definition

	/**
	 * Returns the only process of a scenario that is shown as that process rather than as a group.
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
