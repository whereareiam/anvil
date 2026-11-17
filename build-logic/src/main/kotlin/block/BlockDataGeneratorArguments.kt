package me.whereareiam.anvil.buildlogic.block

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/**
 * The argument of a module's block data generator: its request as one JSON object, naming the directory to write into
 * and each `<protocol>.blocks` the physics harness read from a vanilla client.
 */
abstract class BlockDataGeneratorArguments : CommandLineArgumentProvider {
	/**
	 * The block data files, one per protocol.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NAME_ONLY)
	abstract val blocks: ConfigurableFileCollection

	/**
	 * The generated resource root.
	 */
	@get:OutputDirectory
	abstract val destination: DirectoryProperty

	/**
	 * The package the data belongs to, relative to [destination], such as `me/whereareiam/anvil/protocol/java/block`.
	 */
	@get:Input
	abstract val packagePath: Property<String>

	override fun asArguments(): Iterable<String> {
		val files = blocks.files.sortedBy { it.name }
		if (files.isEmpty()) throw GradleException("There is no block data; run the physics harness's generateVanillaData")

		val request = linkedMapOf<String, Any>(
			"output" to destination.get().asFile.resolve(packagePath.get()).absolutePath,
			"blocks" to files.map { it.absolutePath },
		)
		return listOf(ObjectMapper().writeValueAsString(request))
	}
}
