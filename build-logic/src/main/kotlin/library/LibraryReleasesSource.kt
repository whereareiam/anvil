package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Reads the releases of one library's releases file. As a value source it is a configuration-cache input:
 * adding or editing a release invalidates the cache.
 */
abstract class LibraryReleasesSource : ValueSource<List<LibraryRelease>, LibraryReleasesSource.Parameters> {
	/**
	 * Location the releases are read from.
	 */
	interface Parameters : ValueSourceParameters {
		/**
		 * The library's `<id>-releases.toml`.
		 */
		val releasesFile: RegularFileProperty
	}

	override fun obtain(): List<LibraryRelease> = LibraryReleaseReader.read(parameters.releasesFile.get().asFile)
}
