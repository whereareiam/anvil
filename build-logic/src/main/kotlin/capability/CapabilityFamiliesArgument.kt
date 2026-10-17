package me.whereareiam.anvil.buildlogic.capability

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes the names of the built-in capability families, as [CapabilityFamily.names] reads them, to a test JVM as
 * the system property `anvil.capabilityFamilies`, separated by commas. The family build files are the input, so
 * adding or renaming a family reruns the tests.
 */
abstract class CapabilityFamiliesArgument : CommandLineArgumentProvider {
	/**
	 * The `anvil-capability` folder. Only the family build files below it are inputs.
	 */
	@get:Internal
	abstract val capabilityDirectory: DirectoryProperty

	/**
	 * The build files of each family below a participant group and of its members.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.RELATIVE)
	val buildFiles: FileTree
		get() = capabilityDirectory.asFileTree.matching {
			CapabilityFamily.groups.forEach { group -> include("$group/*/build.gradle.kts", "$group/*/*/build.gradle.kts") }
		}

	override fun asArguments(): Iterable<String> =
		listOf("-Danvil.capabilityFamilies=" + CapabilityFamily.names(capabilityDirectory.get().asFile).joinToString(","))
}
