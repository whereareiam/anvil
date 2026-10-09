package me.whereareiam.anvil.buildlogic.adapter

import me.whereareiam.anvil.buildlogic.library.LibraryRegistry
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Reads a library side folder against the build's library registry. As a value source it is a
 * configuration-cache input: registering a library or adding a segment folder invalidates the cache.
 */
abstract class LibrarySideSource : ValueSource<LibrarySide, LibrarySideSource.Parameters> {
	/**
	 * Locations the side folder is read from.
	 */
	interface Parameters : ValueSourceParameters {
		/**
		 * Root project directory, which holds the library registry.
		 */
		val rootDirectory: DirectoryProperty

		/**
		 * The side folder.
		 */
		val sideDirectory: DirectoryProperty
	}

	override fun obtain(): LibrarySide = LibrarySide.read(
		parameters.sideDirectory.get().asFile,
		LibraryRegistry.discover(parameters.rootDirectory.get().asFile).keys,
	)
}
