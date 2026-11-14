package me.whereareiam.anvil.buildlogic.capability

import org.gradle.api.GradleException
import java.io.File
import java.io.Serializable

/**
 * A built-in capability family, such as `movement` in the folder `builtin-movement`: the folder whose project only
 * wires its member projects.
 *
 * - `<name>-api` holds the public capability API and, for capabilities with a worker side, the channel its host and
 *   worker share;
 * - `<name>-common` holds the host provider, which no protocol edition changes;
 * - `<name>-<edition>` holds the worker extension of one protocol edition, such as `<name>-java` over the Java
 *   Edition protocol.
 *
 * A process family, whose capability a server or proxy owns, may instead need its platforms' own APIs, which the
 * agent reaches through an agent extension:
 *
 * - `<name>-agent` holds the agent channel: the operations and payloads its host provider and platform sides share,
 *   and the platform-neutral handlers, compiled for the oldest Java an agent runs on;
 * - `<name>-<platform>` holds the agent extension of one agent platform, such as `<name>-bukkit` for the agent Paper
 *   and Spigot share, over that platform's API.
 *
 * A family root holds no code, an agent-backed provider included: that lives in `<name>-common` as well.
 *
 * @property name feature name, the family folder's name without the `builtin-` prefix
 * @property api project name of the family API
 * @property common project name of the edition-neutral code, or `null` when the family has none
 * @property sides project names of the edition sides, ordered by name
 * @property agent project name of the agent channel, or `null` when the family has none
 * @property platformSides project names of the agent platform sides, ordered by name
 */
data class CapabilityFamily(
	val name: String,
	val api: String,
	val common: String?,
	val sides: List<String>,
	val agent: String? = null,
	val platformSides: List<String> = emptyList(),
) : Serializable {
	/**
	 * The member projects the family publishes, each as `builtin-<project>`: its API, its agent channel, and its
	 * edition and platform sides. The common project is embedded into the root's JAR instead.
	 */
	val published: List<String>
		get() = listOf(api) + listOfNotNull(agent) + sides + platformSides

	/**
	 * The protocol editions the family's sides adapt it to, such as `java` for `<name>-java`.
	 */
	val editions: List<String>
		get() = sides.map { side -> side.removePrefix("$name-") }

	/**
	 * The agent platforms the family's platform sides adapt it to, such as `bukkit` for `<name>-bukkit`.
	 */
	val platforms: List<String>
		get() = platformSides.map { side -> side.removePrefix("$name-") }

	companion object {
		private const val serialVersionUID = 1L
		private const val PREFIX = "builtin-"
		private val buildFiles = listOf("build.gradle.kts", "build.gradle")
		private val catchAllPackages = setOf("common", "util")
		private const val PROCESS_GROUP = "capability-process-builtin"

		/**
		 * The folders below `anvil-capability` that hold the built-in families of each participant: what a
		 * simulated player can do or see, and what a server or proxy process can do or know.
		 */
		val groups = listOf("capability-player/capability-player-builtin", "capability-process/capability-process-builtin")

		/**
		 * Names the built-in families below [capabilityDirectory]: each folder of a participant group whose root or
		 * member folders hold a build file.
		 *
		 * @param capabilityDirectory the `anvil-capability` folder
		 * @return family names, ordered by name
		 * @throws GradleException when such a folder is not named `builtin-<name>`
		 */
		fun names(capabilityDirectory: File): List<String> = groups
			.flatMap { group -> File(capabilityDirectory, group).listFiles(File::isDirectory).orEmpty().toList() }
			.filter { family -> hasBuildFile(family) || projectFolders(family).isNotEmpty() }
			.map(::featureName)
			.sorted()

		/**
		 * Reads the family folder at [directory]. Every child directory with a build file is a member project.
		 *
		 * @param editions the protocol editions a side may adapt the family to
		 * @param platforms the agent platforms a process family's platform side may adapt it to
		 * @throws GradleException when the folder is not named `builtin-<name>`, the family has no API, holds a child
		 * project that is not a member, such as an agent member of a player family, keeps code in its root, or declares
		 * a `common` or `util` package
		 */
		fun read(directory: File, editions: Collection<String>, platforms: Collection<String> = emptyList()): CapabilityFamily {
			val name = featureName(directory)
			val children = projectFolders(directory)
			val api = "$name-api"
			val common = "$name-common"
			val agent = "$name-agent"
			if (api !in children)
				throw GradleException("Capability family '$name' must contain its API project '$api'")

			val process = directory.parentFile?.name == PROCESS_GROUP
			val agentPlatforms = if (process) platforms else emptyList()
			val members = children.filterNot { it == api || it == common || (process && it == agent) }
			members.forEach { member -> requireSide(name, member, editions, agentPlatforms, process) }
			val platformSides = members.filter { member -> member.removePrefix("$name-") in agentPlatforms }
			if (platformSides.isNotEmpty() && agent !in children)
				throw GradleException(
					"Capability family '$name' has the platform sides $platformSides and must contain the agent channel "
						+ "'$agent' they share with its host provider"
				)
			val hasCommon = common in children
			if (File(directory, "src/main/java").exists())
				throw GradleException(
					"Capability family '$name' only wires its members; move the code of its root to '$common'"
				)

			requireFeaturePackages(name, name, directory)
			children.forEach { child -> requireFeaturePackages(name, child, File(directory, child)) }
			return CapabilityFamily(
				name, api, common.takeIf { hasCommon }, members - platformSides.toSet(),
				agent.takeIf { process && it in children }, platformSides,
			)
		}

		private fun featureName(directory: File): String {
			if (!directory.name.startsWith(PREFIX))
				throw GradleException("Capability family folder '${directory.name}' must be named '$PREFIX<feature>'")

			return directory.name.removePrefix(PREFIX)
		}

		private fun projectFolders(directory: File): List<String> =
			directory.listFiles { child -> child.isDirectory && hasBuildFile(child) }.orEmpty().map(File::getName).sorted()

		private fun hasBuildFile(directory: File): Boolean = buildFiles.any { File(directory, it).isFile }

		private fun requireFeaturePackages(family: String, project: String, directory: File) {
			val sources = File(directory, "src").listFiles(File::isDirectory).orEmpty()
				.flatMap { sourceSet -> listOf(File(sourceSet, "java"), File(sourceSet, "kotlin")) }
				.filter(File::isDirectory)
			sources.forEach { root ->
				val catchAll = root.walkTopDown().firstOrNull { it.isDirectory && it != root && it.name in catchAllPackages }
					?: return@forEach
				val packageName = catchAll.relativeTo(root).invariantSeparatorsPath.replace('/', '.')
				throw GradleException(
					"Capability family '$family' declares the catch-all package '$packageName' in '$project'; "
						+ "keep its code in the feature packages, such as the feature root package for common code"
				)
			}
		}

		private fun requireSide(
			family: String,
			member: String,
			editions: Collection<String>,
			platforms: Collection<String>,
			process: Boolean,
		) {
			val suffix = member.removePrefix("$family-")
			if (member.startsWith("$family-") && (suffix in editions || suffix in platforms)) return

			if (!process)
				throw GradleException(
					"Capability family '$family' may contain only '$family-api', '$family-common' and '$family-<edition>' "
						+ "projects for the protocol editions ${editions.sorted()}; found '$member'"
				)
			throw GradleException(
				"Capability family '$family' may contain only '$family-api', '$family-common', '$family-agent', "
					+ "'$family-<edition>' projects for the protocol editions ${editions.sorted()} and '$family-<platform>' "
					+ "projects for the agent platforms ${platforms.sorted()}; found '$member'"
			)
		}
	}
}
