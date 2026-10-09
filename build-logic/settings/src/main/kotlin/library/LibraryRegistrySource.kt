package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File

/**
 * Lists the build's registered protocol libraries with their releases files. As a value source it is a
 * configuration-cache input: registering a library invalidates the cache.
 */
abstract class LibraryRegistrySource : ValueSource<Map<String, File>, LibraryRegistrySource.Parameters> {
	/**
	 * Location the registry is read from.
	 */
	interface Parameters : ValueSourceParameters {
		/**
		 * Root directory of the build.
		 */
		val rootDirectory: DirectoryProperty
	}

	override fun obtain(): Map<String, File> = LibraryRegistry.discover(parameters.rootDirectory.get().asFile)
}
