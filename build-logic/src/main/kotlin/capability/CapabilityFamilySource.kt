package me.whereareiam.anvil.buildlogic.capability

import me.whereareiam.anvil.buildlogic.library.LibraryRegistry
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Reads a capability family folder against the build's protocol editions and library registry. As a value source it is
 * a configuration-cache input: adding a member project, an edition or a library invalidates the cache.
 */
abstract class CapabilityFamilySource : ValueSource<CapabilityFamily, CapabilityFamilySource.Parameters> {
	/**
	 * Locations the family is read from.
	 */
	interface Parameters : ValueSourceParameters {
		/**
		 * Root project directory, which holds the protocol editions and the library registry.
		 */
		val rootDirectory: DirectoryProperty

		/**
		 * The family folder.
		 */
		val familyDirectory: DirectoryProperty
	}

	override fun obtain(): CapabilityFamily {
		val root = parameters.rootDirectory.get().asFile

		return CapabilityFamily.read(
			parameters.familyDirectory.get().asFile,
			ProtocolEditions.discover(root) + LibraryRegistry.discover(root).keys,
		)
	}
}
