package me.whereareiam.anvil.buildlogic.platform

import java.io.File

/**
 * The projects whose main resources ship platform version data declaring the agent a provider installs: a
 * `<platform>-versions.toml` file with an `[agent]` table. Each must apply `module-platform-provider`, the only convention
 * that checks the declared minimum Java against the agent's classes, so such data in any other project would go
 * unchecked.
 *
 * Only the line declaring the table is read here, which keeps the settings classpath free of a TOML parser: an
 * `[agent]` header, an `agent = { ... }` inline table or a dotted `agent.` key. A dotted key inside another table
 * also counts, which can only demand the convention of a project that ships version data anyway. Platform
 * planning reads the whole file, and the convention checks the declared value.
 *
 * @property agentData data files declaring an agent, relative to the main resources, keyed by project path
 */
class PlatformLayout(val agentData: Map<String, List<String>>) {
	/**
	 * Explains why the project at [path] breaks the layout, or returns `null` when it ships no agent data or
	 * applies the convention.
	 *
	 * @param applied tests whether the project applies a plugin, by id
	 */
	fun violation(path: String, applied: (String) -> Boolean): String? {
		val data = agentData[path] ?: return null
		if (applied(providerConvention)) return null

		return "$path ships platform version data $data but does not apply id(\"$providerConvention\"), " +
			"which checks the platform agent against it"
	}

	companion object {
		private const val providerConvention = "module-platform-provider"
		private const val dataSuffix = "-versions.toml"
		private val agentTable = Regex("""\s*(\[\s*agent\s*]\s*(#.*)?|agent\s*[.=].*)""")

		/**
		 * Derives the layout of the projects in [directories].
		 *
		 * @param directories project folder of each project, keyed by project path
		 */
		fun of(directories: Map<String, File>): PlatformLayout =
			PlatformLayout(directories.mapValues { (_, directory) -> agentData(directory) }.filterValues { it.isNotEmpty() })

		/**
		 * Lists the version data in the main resources of the project at [directory] that declares an agent.
		 *
		 * @return paths relative to the main resources, sorted
		 */
		fun agentData(directory: File): List<String> {
			val resources = File(directory, "src/main/resources")
			if (!resources.isDirectory) return emptyList()

			return resources.walkTopDown()
				.filter { it.isFile && it.name.endsWith(dataSuffix) }
				.filter { file -> file.useLines { lines -> lines.any(agentTable::matches) } }
				.map { it.relativeTo(resources).invariantSeparatorsPath }
				.sorted()
				.toList()
		}
	}
}
