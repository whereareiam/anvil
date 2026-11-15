package me.whereareiam.anvil.buildlogic.block

import com.fasterxml.jackson.databind.ObjectMapper
import me.whereareiam.anvil.buildlogic.download.Checksums
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/**
 * The argument of `protocol-java-block`'s block data generator: its request as JSON, naming the facts, the
 * directory to write into, the pinned minecraft-data release, and for each pinned protocol the files Gradle resolved.
 *
 * Gradle resolves the files `block-data.toml` pins through the settings' `minecraft-data` repository; [minecraftData]
 * holds them, and each pin is found among them by its sha256, so a file whose bytes differ from its pin fails the
 * build before the generator runs. A protocol without pins is passed as missing.
 */
abstract class BlockDataGeneratorArguments : CommandLineArgumentProvider {
	/**
	 * `block-data.toml`.
	 */
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val pins: RegularFileProperty

	/**
	 * `block-facts.toml`.
	 */
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val facts: RegularFileProperty

	/**
	 * The minecraft-data files Gradle resolved for the pins.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val minecraftData: ConfigurableFileCollection

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
		val pinned = BlockDataPinsFile.read(pins.get().asFile)
		if (pinned.source.commit.isEmpty()) throw GradleException("block-data.toml has no commit yet; run pinBlockData")
		if (pinned.releases.none { it.pinned }) throw GradleException("block-data.toml pins no protocol")

		val resolved = minecraftData.files.associateBy { Checksums.sha256(it) }
		val request = Request(
			facts = facts.get().asFile.absolutePath,
			output = destination.get().asFile.resolve(packagePath.get()).absolutePath,
			release = Release(pinned.source.repository, pinned.source.version, pinned.source.commit),
			protocols = pinned.releases.filter { it.pinned }.map { release ->
				PinnedProtocol(release.protocol, file(resolved, release.blocks!!), file(resolved, release.collisionShapes!!))
			},
			missing = pinned.releases.filterNot { it.pinned }.map { it.protocol },
		)

		return listOf(ObjectMapper().writeValueAsString(request))
	}

	private fun file(resolved: Map<String, File>, pin: BlockDataPins.PinnedFile): PinnedFile {
		val file = resolved[pin.sha256] ?: throw GradleException("Gradle resolved no minecraft-data ${pin.path} with the "
			+ "sha256 ${pin.sha256} that block-data.toml pins; pins never change silently, so check the file and run "
			+ "pinBlockData only on purpose")

		return PinnedFile(file.absolutePath, pin.path)
	}

	/**
	 * The generator's `GenerationRequest`, as JSON.
	 */
	private data class Request(
		val facts: String,
		val output: String,
		val release: Release,
		val protocols: List<PinnedProtocol>,
		val missing: List<Int>,
	)

	private data class Release(val repository: String, val version: String, val commit: String)

	private data class PinnedProtocol(val protocol: Int, val blocks: PinnedFile, val collisionShapes: PinnedFile)

	private data class PinnedFile(val file: String, val path: String)
}
