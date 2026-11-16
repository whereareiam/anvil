package me.whereareiam.anvil.buildlogic.matrix

import me.whereareiam.anvil.buildlogic.library.LibraryReleaseReader
import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes the release data a protocol library ships: its `<id>-releases.toml` as written by hand, with each
 * `[[release]]` row's `verified` versions after its `minecraft` line. A release is verified on every Minecraft
 * version it speaks that a server of [matrix] runs, whatever the platform, because the live suites join each of
 * them with the release's players.
 *
 * The live matrix is the only list of verified combinations: the releases file declares no `verified` itself, which
 * [LibraryReleaseReader] refuses, and a server version no release speaks fails the task.
 */
@CacheableTask
abstract class WriteVerifiedReleases : DefaultTask() {
	/**
	 * The library's hand-written `<id>-releases.toml`.
	 */
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NAME_ONLY)
	abstract val releases: RegularFileProperty

	/**
	 * `live-matrix.toml`.
	 */
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val matrix: RegularFileProperty

	/**
	 * The release data to ship.
	 */
	@get:OutputFile
	abstract val destination: RegularFileProperty

	@TaskAction
	fun write() {
		val source = releases.get().asFile
		val spoken = LibraryReleaseReader.read(source).flatMap { it.minecraft }.toSet()
		val servers = LiveMatrix.read(matrix.get().asFile).serverVersions.map(MinecraftVersion::parse).toSet()
		(servers - spoken).sorted().forEach {
			throw GradleException(
				"live-matrix.toml runs a server of Minecraft $it, which no release of ${source.name} speaks")
		}

		destination.get().asFile.writeText(withVerified(source.readLines(), servers))
	}

	internal companion object {
		private const val RELEASE = "[[release]]"
		private val header = Regex("""\s*\[.*]\s*""")
		private val minecraftLine = Regex("""minecraft\s*=\s*\[(.*)]\s*""")
		private val versions = Regex("\"([^\"]+)\"")

		/**
		 * Adds a `verified` line after the `minecraft` line of each `[[release]]` row, which the releases file's
		 * formatter writes on one line: the versions it lists that a server of the matrix runs.
		 */
		fun withVerified(lines: List<String>, servers: Set<MinecraftVersion>): String = buildString {
			var table = ""
			lines.forEach { line ->
				if (header.matches(line)) table = line.trim()
				appendLine(line)
				val minecraft = minecraftLine.matchEntire(line) ?: return@forEach
				if (table != RELEASE) return@forEach

				val verified = versions.findAll(minecraft.groupValues[1]).map { MinecraftVersion.parse(it.groupValues[1]) }
					.filter(servers::contains)
					.sorted()
				appendLine("verified = [${verified.joinToString(", ") { "\"$it\"" }}]")
			}
		}
	}
}
