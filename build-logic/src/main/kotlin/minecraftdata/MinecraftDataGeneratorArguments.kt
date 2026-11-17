package me.whereareiam.anvil.buildlogic.minecraftdata

import com.fasterxml.jackson.databind.ObjectMapper
import me.whereareiam.anvil.buildlogic.download.Checksums
import me.whereareiam.anvil.buildlogic.minecraftdata.pin.MinecraftDataPins
import me.whereareiam.anvil.buildlogic.minecraftdata.pin.MinecraftDataPinsFile
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

/**
 * The argument of a module's minecraft-data generator: its request as one JSON object, naming the directory to write
 * into, the pinned minecraft-data release, for each pinned protocol the files Gradle resolved by their fields, the
 * protocols minecraft-data has no data for, and the module's own further inputs by name, such as `block-facts.toml`
 * as `facts`.
 *
 * Gradle resolves the pinned files through the settings' `minecraft-data` repository; [minecraftData] holds them, and
 * each pin is found among them by its sha256, so a file whose bytes differ from its pin fails the build before the
 * generator runs.
 */
abstract class MinecraftDataGeneratorArguments : CommandLineArgumentProvider {
	/**
	 * The kind the generator generates.
	 */
	@get:Input
	abstract val kind: Property<MinecraftDataKind>

	/**
	 * The kind's pins file.
	 */
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val pins: RegularFileProperty

	/**
	 * The minecraft-data files Gradle resolved for the pins.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val minecraftData: ConfigurableFileCollection

	/**
	 * The module's further input files by the request member that names them.
	 */
	@get:Internal
	abstract val inputs: MapProperty<String, RegularFile>

	/**
	 * The request members that name the further input files.
	 */
	@get:Input
	val inputNames: Provider<List<String>>
		get() = inputs.map { it.keys.sorted() }

	/**
	 * The further input files.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NONE)
	val inputFiles: Provider<Collection<RegularFile>>
		get() = inputs.map { it.values }

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
		val kind = kind.get()
		val pinned = MinecraftDataPinsFile(kind).read(pins.get().asFile)
		if (pinned.source.commit.isEmpty()) throw GradleException("${kind.pinsFile} has no commit yet; run pin${kind.stem}")
		if (pinned.releases.none { it.pinned }) throw GradleException("${kind.pinsFile} pins no protocol")

		val resolved = minecraftData.files.associateBy { Checksums.sha256(it) }
		val request = linkedMapOf<String, Any>(
			"output" to destination.get().asFile.resolve(packagePath.get()).absolutePath,
			"release" to Release(pinned.source.repository, pinned.source.version, pinned.source.commit),
			"protocols" to pinned.releases.filter { it.pinned }.map { release ->
				PinnedProtocol(release.protocol, release.files.mapValues { (_, pin) -> file(kind, resolved, pin) })
			},
			"missing" to pinned.releases.filterNot { it.pinned }.map { it.protocol },
		)
		inputs.get().forEach { (name, input) -> request[name] = input.asFile.absolutePath }

		return listOf(ObjectMapper().writeValueAsString(request))
	}

	private fun file(kind: MinecraftDataKind, resolved: Map<String, File>, pin: MinecraftDataPins.PinnedFile): PinnedFile {
		val file = resolved[pin.sha256] ?: throw GradleException("Gradle resolved no minecraft-data ${pin.path} with the "
			+ "sha256 ${pin.sha256} that ${kind.pinsFile} pins; pins never change silently, so check the file and run "
			+ "pin${kind.stem} only on purpose")

		return PinnedFile(file.absolutePath, pin.path)
	}

	private data class Release(val repository: String, val version: String, val commit: String)

	private data class PinnedProtocol(val protocol: Int, val files: Map<String, PinnedFile>)

	private data class PinnedFile(val file: String, val path: String)
}
