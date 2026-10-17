package me.whereareiam.anvil.buildlogic.capability

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Reads a capability family folder against the build's protocol editions. As a value source it is a
 * configuration-cache input: adding a member project or an edition invalidates the cache.
 */
abstract class CapabilityFamilySource : ValueSource<CapabilityFamily, CapabilityFamilySource.Parameters> {
	/**
	 * Locations the family is read from.
	 */
	interface Parameters : ValueSourceParameters {
		/**
		 * Root project directory, which holds the protocol editions.
		 */
		val rootDirectory: DirectoryProperty

		/**
		 * The family folder.
		 */
		val familyDirectory: DirectoryProperty
	}

	override fun obtain(): CapabilityFamily = CapabilityFamily.read(
		parameters.familyDirectory.get().asFile,
		ProtocolEditions.discover(parameters.rootDirectory.get().asFile),
	)
}
