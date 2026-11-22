package me.whereareiam.anvil.buildlogic.module

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes one directory to a test JVM as a system property holding its absolute path, such as data files every test of
 * a suite reads. The files' contents and relative paths are the input and the directory's location is not, so the
 * build cache stays relocatable.
 *
 * ```kotlin
 * tasks.named<Test>("test") {
 *     jvmArgumentProviders.add(objects.newInstance<TestDirectoryArgument>().apply {
 *         property.set("anvil.physics.courses")
 *         directory.set(layout.settingsDirectory.dir("anvil-testkit/physics/courses"))
 *     })
 * }
 * ```
 */
abstract class TestDirectoryArgument : CommandLineArgumentProvider {
	/**
	 * Name of the system property.
	 */
	@get:Input
	abstract val property: Property<String>

	/**
	 * The directory the property locates.
	 */
	@get:InputDirectory
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val directory: DirectoryProperty

	override fun asArguments(): Iterable<String> = listOf("-D${property.get()}=${directory.get().asFile.absolutePath}")
}
