package me.whereareiam.anvil.buildlogic.capability

import java.io.File

/**
 * The platform families whose processes run a platform agent: each `platform-<name>` folder below
 * `anvil-process/process-platform` that holds a `<name>-agent` project, such as `bukkit` for the agent Paper and
 * Spigot share. A built-in process capability family adapts its agent side to such a platform with its
 * `<feature>-<platform>` side.
 */
object AgentPlatforms {
	private const val DIRECTORY = "anvil-process/process-platform"
	private const val PREFIX = "platform-"

	/**
	 * Names the agent platforms of the build at [rootDirectory].
	 *
	 * @param rootDirectory root project directory
	 * @return platform family names, ordered by name
	 */
	fun discover(rootDirectory: File): List<String> = File(rootDirectory, DIRECTORY)
		.listFiles { file -> file.isDirectory && file.name.startsWith(PREFIX) }.orEmpty()
		.map { directory -> directory.name.removePrefix(PREFIX) to directory }
		.filter { (name, directory) -> File(directory, "$name-agent").isDirectory }
		.map { (name, _) -> name }
		.sorted()
}
