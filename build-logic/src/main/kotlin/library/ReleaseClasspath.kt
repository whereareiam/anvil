package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input

/**
 * The resolved runtime closure of one library release as a task input, such as the locked closure a segment's
 * linkage is checked against or a worker test runs on.
 */
abstract class ReleaseClasspath {
	/**
	 * The library's release identifier, such as `1.19.4-1`.
	 */
	@get:Input
	abstract val version: Property<String>

	/**
	 * The release key, the highest Minecraft version the release speaks.
	 */
	@get:Input
	abstract val minecraft: Property<String>

	/**
	 * The closure's JARs in classpath order.
	 */
	@get:Classpath
	abstract val files: ConfigurableFileCollection
}
