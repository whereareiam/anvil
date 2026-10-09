package me.whereareiam.anvil.buildlogic.library

import java.io.File

/**
 * The conventions the projects of the build's protocol libraries must apply, derived from the project tree:
 *
 * - the family root of each registered library, the project whose folder owns `<id>-releases.toml`, applies
 *   `module-library`, which pins that release data;
 * - a library side folder, any project holding segment projects named `V<major>_<minor>[_<patch>]`, applies
 *   `module-adapter`, which is the only way its segments reach a worker's class path.
 *
 * @property conventions the convention each such project must apply, keyed by project path
 */
class LibraryLayout private constructor(val conventions: Map<String, String>) {
	/**
	 * One project of the build.
	 *
	 * @property path project path, such as `:anvil-protocol:protocol-mcprotocol`
	 * @property directory project folder
	 * @property children names of the child projects
	 */
	data class Project(val path: String, val directory: File, val children: Collection<String>)

	/**
	 * Explains why the project at [path] breaks the layout, or returns `null` when it applies its convention.
	 *
	 * @param applied tests whether the project applies a plugin, by id
	 */
	fun violation(path: String, applied: (String) -> Boolean): String? {
		val convention = conventions[path] ?: return null
		if (applied(convention)) return null

		return when (convention) {
			releasesConvention -> "$path owns the release data of a protocol library and must apply id(\"$releasesConvention\"), which pins it"
			else -> "$path holds segments, which reach a worker only through their library side folder; it must apply id(\"$convention\")"
		}
	}

	companion object {
		private const val releasesConvention = "module-library"
		private const val sideConvention = "module-adapter"

		/**
		 * The shape of a segment's folder and project name. Segment conventions validate the version it names.
		 */
		val segmentName = Regex("""V\d+_\d+(_\d+)?""")

		/**
		 * Derives the layout of [projects] for the registered [libraries].
		 *
		 * @param libraries releases file of each registered library, keyed by library id
		 */
		fun of(projects: Collection<Project>, libraries: Map<String, File>): LibraryLayout {
			val familyRoots = libraries.values.map { it.parentFile.absoluteFile }.toSet()
			val conventions = linkedMapOf<String, String>()
			for (project in projects) {
				if (project.directory.absoluteFile in familyRoots) conventions[project.path] = releasesConvention
				if (project.children.any(segmentName::matches)) conventions[project.path] = sideConvention
			}
			return LibraryLayout(conventions)
		}
	}
}
