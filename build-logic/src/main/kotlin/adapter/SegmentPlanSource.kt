package me.whereareiam.anvil.buildlogic.adapter

import me.whereareiam.anvil.buildlogic.library.LibraryRegistry
import me.whereareiam.anvil.buildlogic.library.LibraryReleaseReader
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Plans one segment from its folder, its sibling folders and the library's releases file. As a value source
 * it is a configuration-cache input: editing release data or adding a sibling segment invalidates the cache.
 */
abstract class SegmentPlanSource : ValueSource<SegmentPlan, SegmentPlanSource.Parameters> {
	/**
	 * Locations the plan is read from.
	 */
	interface Parameters : ValueSourceParameters {
		/**
		 * Root project directory, which holds the library registry.
		 */
		val rootDirectory: DirectoryProperty

		/**
		 * The segment's own folder.
		 */
		val segmentDirectory: DirectoryProperty
	}

	override fun obtain(): SegmentPlan {
		val directory = parameters.segmentDirectory.get().asFile
		val segment = SegmentName.parse(directory.name)
		val libraries = LibraryRegistry.discover(parameters.rootDirectory.get().asFile)
		val side = LibrarySide.read(directory.parentFile, libraries.keys)
		return SegmentPlan.of(side, segment, LibraryReleaseReader.read(libraries.getValue(side.library)))
	}
}
