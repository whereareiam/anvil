package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes the file written by [WriteReleaseClosures] to a test JVM as the system property
 * `anvil.test.<library>.closures`. The closures themselves are the test input: a changed JAR reruns the tests,
 * while the machine-specific paths the file holds do not take part, so test results stay relocatable.
 */
abstract class ReleaseClosuresArgument : CommandLineArgumentProvider {
	/**
	 * Registered library id.
	 */
	@get:Input
	abstract val library: Property<String>

	/**
	 * The locked closure of each release the tests start workers on.
	 */
	@get:Nested
	abstract val releases: ListProperty<ReleaseClasspath>

	/**
	 * The closures file. It only locates [releases] on this machine, so it is not an input; the test task depends
	 * on the task writing it.
	 */
	@get:Internal
	abstract val closures: RegularFileProperty

	override fun asArguments(): Iterable<String> =
		listOf("-Danvil.test.${library.get()}.closures=${closures.get().asFile.absolutePath}")
}
