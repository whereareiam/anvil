package me.whereareiam.anvil.buildlogic.minecraftdata

import me.whereareiam.anvil.buildlogic.library.LibraryReleaseReader
import me.whereareiam.anvil.buildlogic.minecraftdata.pin.MinecraftDataPinsFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Checks that one kind's pins file, such as `packet-data.toml`, lists exactly the protocols the protocol libraries
 * speak: one row for the protocol of each release of a library's `<id>-releases.toml`. A protocol a library adds
 * without a row, or a row no library speaks, fails the check, so a supported release can never lack the data
 * silently; a row without pins reports its protocol as missing instead. A Minecraft version a library adds to a
 * protocol it already speaks needs no row.
 */
abstract class CheckMinecraftDataReleases : DefaultTask() {
	/**
	 * The kind the pins file pins.
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
		val name = kind.get().pinsFile
		val rows = MinecraftDataPinsFile(kind.get()).read(pins.get().asFile).releases.map { it.protocol }.toSet()
		val releases = libraryReleases.files.flatMap { file ->
			LibraryReleaseReader.read(file).map { it.protocol to "${file.name} ${it.version}" }
		}
		val unlisted = releases.filter { (protocol, _) -> protocol !in rows }
			.map { (protocol, release) -> "$release speaks protocol $protocol, which no row of $name lists" }
		val unknown = rows.filter { row -> releases.none { it.first == row } }
			.map { "$name lists protocol $it, which no protocol library release speaks" }
		val problems = unlisted + unknown
		if (problems.isNotEmpty())
			throw GradleException("$name must list the protocol of each protocol library release:\n"
				+ problems.joinToString("\n") { "  $it" })

		marker.get().asFile.writeText("$name lists every protocol library release's protocol\n")
	}
}
