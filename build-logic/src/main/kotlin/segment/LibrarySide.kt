package me.whereareiam.anvil.buildlogic.segment

import me.whereareiam.anvil.buildlogic.library.LibraryRegistry
import org.gradle.api.GradleException
import java.io.File
import java.io.Serializable

/**
 * A library side folder, such as `messages-mcprotocol`: the project that holds one library's segments as its
 * only child projects.
 *
 * @property name folder and project name
 * @property library the registered library the folder names
 * @property segments child segments, ordered by start version
 */
data class LibrarySide(val name: String, val library: String, val segments: List<SegmentName>) : Serializable {
	companion object {
		private const val serialVersionUID = 1L
		private val buildFiles = listOf("build.gradle.kts", "build.gradle")

		/**
		 * Reads the side folder at [directory]. Every child directory with a build file is a child project and
		 * must be a segment.
		 *
		 * @param libraries registered library ids
		 * @throws GradleException when the folder names no single library, holds no segment, or holds another
		 * child project
		 */
		fun read(directory: File, libraries: Collection<String>): LibrarySide {
			val library = LibraryRegistry.libraryOf(directory.name, libraries)
			val children = directory.listFiles { child -> child.isDirectory && buildFiles.any { File(child, it).isFile } }
				.orEmpty().map(File::getName).sorted()
			if (children.isEmpty())
				throw GradleException("Library side folder '${directory.name}' must contain at least one segment project")
			val segments = children.map { child ->
				try {
					SegmentName.parse(child)
				} catch (exception: GradleException) {
					throw GradleException("Library side folder '${directory.name}' may contain only segments: ${exception.message}", exception)
				}
			}
			return LibrarySide(directory.name, library, segments.sortedBy(SegmentName::since))
		}
	}
}
