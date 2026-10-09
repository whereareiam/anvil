package me.whereareiam.anvil.buildlogic.module

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes one file to a test JVM as a system property holding its absolute path, such as a documentation page a
 * test compares with the code. The file's content is the input and its location is not, so the build cache stays
 * relocatable.
 *
 * ```kotlin
 * tasks.named<Test>("test") {
 *     jvmArgumentProviders.add(objects.newInstance<TestFileArgument>().apply {
 *         property.set("anvil.docs.javaGuide")
 *         file.set(layout.settingsDirectory.file("docs/content/java/index.md"))
 *     })
 * }
 * ```
 */
abstract class TestFileArgument : CommandLineArgumentProvider {
	/**
	 * Name of the system property.
	 */
	@get:Input
	abstract val property: Property<String>

	/**
	 * The file the property locates.
	 */
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val file: RegularFileProperty

	override fun asArguments(): Iterable<String> = listOf("-D${property.get()}=${file.get().asFile.absolutePath}")
}
