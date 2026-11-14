package me.whereareiam.anvil.buildlogic.capability

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Reads a capability family folder against the build's protocol editions and agent platforms. As a value source it is
 * a configuration-cache input: adding a member project, an edition or an agent platform invalidates the cache.
 */
abstract class CapabilityFamilySource : ValueSource<CapabilityFamily, CapabilityFamilySource.Parameters> {
	/**
	 * Locations the family is read from.
	 */
	interface Parameters : ValueSourceParameters {
		/**
		 * Root project directory, which holds the protocol editions and the agent platforms.
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
		AgentPlatforms.discover(parameters.rootDirectory.get().asFile),
	)
}
