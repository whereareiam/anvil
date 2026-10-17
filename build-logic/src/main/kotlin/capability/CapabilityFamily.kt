package me.whereareiam.anvil.buildlogic.capability

import org.gradle.api.GradleException
import java.io.File
import java.io.Serializable

/**
 * A built-in capability family, such as `movement` in the folder `builtin-movement`: the folder whose project only
 * wires its member projects.
 *
 * - `<name>-api` holds the public capability API and, for packet capabilities, its library-neutral port;
 * - `<name>-common` holds the library-neutral host provider and worker binding;
 * - `<name>-<library>` adapts the family to one registered protocol library through its segments.
 *
 * A family root holds no code, an agent-backed provider included: that lives in `<name>-common` as well.
 *
 * @property name feature name, the family folder's name without the `builtin-` prefix
 * @property api project name of the family API
 * @property common project name of the library-neutral code, or `null` when the family has none
 * @property sides project names of the library sides, ordered by name
 */
data class CapabilityFamily(
	val name: String,
	val api: String,
	val common: String?,
	val sides: List<String>,
) : Serializable {
	/**
	 * The member projects the family publishes, each as `builtin-<project>`: its API and its library sides. The
	 * common project is embedded into the root's JAR instead.
	 */
	val published: List<String>
		get() = listOf(api) + sides

	companion object {
		private const val serialVersionUID = 1L
		private const val PREFIX = "builtin-"
		private val buildFiles = listOf("build.gradle.kts", "build.gradle")
		private val catchAllPackages = setOf("common", "util")

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
		 * @param libraries registered library ids
		 * @throws GradleException when the folder is not named `builtin-<name>`, the family has no API, holds a child
		 * project that is not a member, keeps code in its root, or declares a `common` or `util` package
		 */
		fun read(directory: File, libraries: Collection<String>): CapabilityFamily {
			val name = featureName(directory)
			val children = projectFolders(directory)
			val api = "$name-api"
			val common = "$name-common"
			if (api !in children)
				throw GradleException("Capability family '$name' must contain its API project '$api'")

			val sides = children.filterNot { it == api || it == common }
			sides.forEach { side -> requireSide(name, side, libraries) }
			val hasCommon = common in children
			if (File(directory, "src/main/java").exists())
				throw GradleException(
					"Capability family '$name' only wires its members; move the code of its root to '$common'"
				)

			requireFeaturePackages(name, name, directory)
			children.forEach { child -> requireFeaturePackages(name, child, File(directory, child)) }
			return CapabilityFamily(name, api, common.takeIf { hasCommon }, sides)
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

		private fun requireSide(family: String, side: String, libraries: Collection<String>) {
			val library = side.removePrefix("$family-")
			if (side.startsWith("$family-") && library in libraries) return

			throw GradleException(
				"Capability family '$family' may contain only '$family-api', '$family-common' and '$family-<library>' "
					+ "projects for registered libraries ${libraries.sorted()}; found '$side'"
			)
		}
	}
}
