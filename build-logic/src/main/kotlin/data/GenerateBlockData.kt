package me.whereareiam.anvil.buildlogic.data

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Generates the block data resources of `protocol-java-data` from the pinned minecraft-data files.
 *
 * Each pinned release of `block-data.toml` becomes `blocks-<key>.txt.gz` in [packagePath] below [destination], in
 * the format of [BlockDataEncoder], with the facts of `block-facts.toml` merged in; the files are downloaded at the
 * pinned commit and checked against their sha256 first. `block-data.index` lists each release with its Minecraft
 * versions, and each row without pins as missing, with the reason. Every block the facts name must exist in at
 * least one release, so a misspelt name fails instead of being ignored. The output depends only on the two files,
 * whose pins fix the downloaded bytes, so the task is cacheable.
 */
@CacheableTask
abstract class GenerateBlockData : DefaultTask() {
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
	 * The resource directory the files are written to, relative to [destination], such as
	 * `me/whereareiam/anvil/protocol/java/data`.
	 */
	@get:Input
	abstract val packagePath: Property<String>

	/**
	 * The generated resource root.
	 */
	@get:OutputDirectory
	abstract val destination: DirectoryProperty

	@TaskAction
	fun generate() {
		val pinned = BlockDataPinsFile.read(pins.get().asFile)
		val blockFacts = BlockFactsFile.read(facts.get().asFile)
		if (pinned.source.commit.isEmpty()) throw GradleException("block-data.toml has no commit yet; run pinBlockData")
		if (pinned.releases.none { it.pinned }) throw GradleException("block-data.toml pins no release")

		val directory = destination.get().asFile.resolve(packagePath.get())
		directory.deleteRecursively()
		directory.mkdirs()
		val json = ObjectMapper()
		val names = mutableSetOf<String>()
		MinecraftDataRepository(pinned.source.repository).use { repository ->
			pinned.releases.filter { it.pinned }.forEach { release ->
				val blocks = json.readTree(repository.read(pinned.source.commit, release.blocks!!))
				val shapes = json.readTree(repository.read(pinned.source.commit, release.collisionShapes!!))
				val encoded = BlockDataEncoder.encode(pinned.source, release, blocks, shapes, blockFacts)
				write(directory.resolve("blocks-${release.key}.txt.gz"), encoded.text)
				names += encoded.names
			}
		}
		blockFacts.blocks.keys.filterNot(names::contains).sorted().forEach {
			throw GradleException("block-facts.toml names $it, which no release of block-data.toml has")
		}

		directory.resolve("block-data.index").writeText(index(pinned))
	}

	private fun write(file: File, text: String) {
		GZIPOutputStream(file.outputStream()).use { it.write(text.toByteArray(Charsets.UTF_8)) }
	}

	private fun index(pins: BlockDataPins): String = buildString {
		append("anvil-block-data-index ${BlockDataEncoder.FORMAT}\n")
		pins.releases.forEach { release ->
			val versions = release.minecraft.sorted().joinToString(",")
			if (release.pinned) append("data ${release.key} $versions\n")
			else append("missing $versions ${pins.source.repository} ${pins.source.version} has no block data for these versions\n")
		}
	}
}
