package me.whereareiam.anvil.buildlogic.fixtures

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/** Resolves an exact fixture JAR only when Gradle starts the consuming test JVM. */
abstract class FixtureJvmArguments : CommandLineArgumentProvider {
	@get:Input
	abstract val propertyName: Property<String>

	@get:Classpath
	abstract val artifactFiles: ConfigurableFileCollection

	override fun asArguments(): Iterable<String> = listOf("-D${propertyName.get()}=${artifactFiles.singleFile.absolutePath}")
}
