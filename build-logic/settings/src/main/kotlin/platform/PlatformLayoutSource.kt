package me.whereareiam.anvil.buildlogic.platform

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File

/**
 * Lists the platform version data declaring an agent in each project's main resources, as
 * [PlatformLayout.agentData] reads it. As a value source it is a configuration-cache input: adding an agent to
 * version data invalidates the cache.
 */
abstract class PlatformLayoutSource : ValueSource<Map<String, List<String>>, PlatformLayoutSource.Parameters> {
	/**
	 * The projects to read.
	 */
	interface Parameters : ValueSourceParameters {
		/**
		 * Absolute project folder of each project, keyed by project path.
		 */
		val projectDirectories: MapProperty<String, String>
	}

	override fun obtain(): Map<String, List<String>> =
		PlatformLayout.of(parameters.projectDirectories.get().mapValues { (_, directory) -> File(directory) }).agentData
}
