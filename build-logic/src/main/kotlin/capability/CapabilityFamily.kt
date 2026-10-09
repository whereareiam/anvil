package me.whereareiam.anvil.buildlogic.capability

import org.gradle.api.GradleException
import java.io.File
import java.io.Serializable

/**
 * A built-in capability family, such as `movement`: the folder whose project only wires its member projects.
 *
 * - `<name>-api` holds the public capability API and, for packet capabilities, its library-neutral port;
 * - `<name>-common` holds the library-neutral host provider and worker binding;
 * - `<name>-<library>` adapts the family to one registered protocol library through its segments.
 *
 * A process family without common code and library sides, such as `console`, keeps its agent-backed provider in
 * the root: that provider binds the family API to another family's agent operations, which only the root, as an
 * assembly, may depend on. Every other family root holds no code.
 *
 * @property name folder and project name
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
		private const val processOwner = "process"
		private val buildFiles = listOf("build.gradle.kts", "build.gradle")
		private val catchAllPackages = setOf("common", "util")

		/**
		 * The folders below `capability-builtin` that group the built-in families by capability owner.
		 */
		val owners = listOf("player", processOwner)

		/**
		 * Names the built-in families below [builtinDirectory]: each folder of an owner group whose root or member
		 * folders hold a build file.
		 *
		 * @param builtinDirectory the `capability-builtin` folder
		 * @return family names, ordered by name
		 */
		fun names(builtinDirectory: File): List<String> = owners
			.flatMap { owner -> File(builtinDirectory, owner).listFiles(File::isDirectory).orEmpty().toList() }
			.filter { family -> hasBuildFile(family) || projectFolders(family).isNotEmpty() }
			.map(File::getName)
			.sorted()

		/**
		 * Reads the family folder at [directory]. Every child directory with a build file is a member project.
		 *
		 * @param libraries registered library ids
		 * @throws GradleException when the family has no API, holds a child project that is not a member, keeps
		 * code in its root although it is not a process family without a common project and library sides, or
		 * declares a `common` or `util` package
		 */
		fun read(directory: File, libraries: Collection<String>): CapabilityFamily {
			val name = directory.name
			val children = projectFolders(directory)
			val api = "$name-api"
			val common = "$name-common"
			if (api !in children)
				throw GradleException("Capability family '$name' must contain its API project '$api'")

			val sides = children.filterNot { it == api || it == common }
			sides.forEach { side -> requireSide(name, side, libraries) }
			val hasCommon = common in children
			val agentBacked = directory.parentFile?.name == processOwner && !hasCommon && sides.isEmpty()
			if (!agentBacked && File(directory, "src/main/java").exists())
				throw GradleException(
					"Capability family '$name' only wires its members; move the code of its root to '$common'"
				)

			requireFeaturePackages(name, name, directory)
			children.forEach { child -> requireFeaturePackages(name, child, File(directory, child)) }
			return CapabilityFamily(name, api, common.takeIf { hasCommon }, sides)
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
