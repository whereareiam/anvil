package me.whereareiam.anvil.buildlogic.library.pin

import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * The worker runtime closure Gradle resolved for one release's module.
 */
abstract class ResolvedRelease {
	/**
	 * The library's release identifier, such as `1.19.4-1`.
	 */
	@get:Input
	abstract val version: Property<String>

	/**
	 * The resolved JARs in classpath order.
	 */
	@get:Internal
	abstract val artifacts: SetProperty<ResolvedArtifactResult>
}
