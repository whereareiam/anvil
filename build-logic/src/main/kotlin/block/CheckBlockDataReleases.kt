package me.whereareiam.anvil.buildlogic.block

import me.whereareiam.anvil.buildlogic.library.LibraryReleaseReader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Checks that the block data read from vanilla clients covers exactly the protocols the protocol libraries speak: one
 * `<protocol>.blocks` for the protocol of each release of a library's `<id>-releases.toml`. A protocol a library adds
 * without block data fails the check, so a supported release never lacks it; so does block data of a protocol no
 * library speaks. A Minecraft version a library adds to a protocol it already speaks needs nothing new, since every
 * version of a protocol shares one block registry.
 */
abstract class CheckBlockDataReleases : DefaultTask() {
	/**
	 * The block data files.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NAME_ONLY)
	abstract val blocks: ConfigurableFileCollection

	/**
	 * The protocol libraries' release files, such as `mcprotocol-releases.toml`.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.NAME_ONLY)
	abstract val libraryReleases: ConfigurableFileCollection

	/**
	 * A marker the task writes once the check passed, so it runs again only when an input changed.
	 */
	@get:OutputFile
	abstract val marker: RegularFileProperty

	@TaskAction
	fun check() {
		val problems = problems(
			blocks.files.map { it.name },
			libraryReleases.files.flatMap { file -> LibraryReleaseReader.read(file).map { it.protocol to "${file.name} ${it.version}" } },
		)
		if (problems.isNotEmpty())
			throw GradleException("The block data read from vanilla clients must cover the protocol of each protocol library release; " +
				"run the physics harness's generateVanillaData for the releases it lacks:\n" + problems.joinToString("\n") { "  $it" })

		marker.get().asFile.writeText("Every protocol library release's protocol has block data\n")
	}

	internal companion object {
		/**
		 * What is wrong with block data files named [files] for the protocol library [releases], each a protocol and
		 * the release that speaks it.
		 */
		fun problems(files: List<String>, releases: List<Pair<Int, String>>): List<String> {
			val malformed = files.filter { it.removeSuffix(BlockDataExtension.SUFFIX).toIntOrNull() == null }
				.map { "$it is not named <protocol>${BlockDataExtension.SUFFIX}" }
			val protocols = files.mapNotNull { it.removeSuffix(BlockDataExtension.SUFFIX).toIntOrNull() }.toSet()
			val missing = releases.filter { (protocol, _) -> protocol !in protocols }
				.map { (protocol, release) -> "$release speaks protocol $protocol, which has no block data" }
			val unknown = protocols.filter { protocol -> releases.none { it.first == protocol } }
				.map { "$it${BlockDataExtension.SUFFIX} holds block data of a protocol no protocol library release speaks" }
			return malformed + missing + unknown
		}
	}
}
