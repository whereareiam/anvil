package me.whereareiam.anvil.buildlogic.matrix

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes the version data a platform provider ships: each `<platform>-versions.toml` of [versionData] as written by
 * hand, followed by the `[verified]` table of what [matrix] runs on that platform, at the same path below
 * [destination]. The platform is the file name's prefix, the provider's id.
 *
 * The live matrix is the only list of verified combinations, so a hand-written `[verified]` table fails the task.
 * A platform the matrix does not run, or runs without a version, as BungeeCord, ships no `[verified]` table.
 */
@CacheableTask
abstract class WriteVerifiedVersionData : DefaultTask() {
	/**
	 * The provider's hand-written version data, rooted at its resource directory.
	 */
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val versionData: ConfigurableFileTree

	/**
	 * `live-matrix.toml`.
	 */
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val matrix: RegularFileProperty

	/**
	 * The generated resource root.
	 */
	@get:OutputDirectory
	abstract val destination: DirectoryProperty

	@TaskAction
	fun write() {
		val liveMatrix = LiveMatrix.read(matrix.get().asFile)
		val root = destination.get().asFile
		root.deleteRecursively()

		versionData.visit {
			if (isDirectory) return@visit
			val text = file.readText()
			if (verifiedTable.containsMatchIn(text))
				throw GradleException("$relativePath declares [verified], which the build writes from live-matrix.toml; "
					+ "list the combination there instead")

			val platform = name.removeSuffix(SUFFIX)
			relativePath.getFile(root).apply { parentFile.mkdirs() }.writeText(text + verifiedTable(platform, liveMatrix))
		}
	}

	internal companion object {
		private const val SUFFIX = "-versions.toml"
		private val verifiedTable = Regex("""(?m)^\s*(\[\s*verified\s*]|verified\s*[.=])""")

		/**
		 * The `[verified]` table of what [matrix] runs on [platform], or nothing when it runs no version of it.
		 */
		fun verifiedTable(platform: String, matrix: LiveMatrix): String {
			val versions = matrix.verified(platform)
			if (versions.isEmpty()) return ""

			return buildString {
				append("\n# Written by the build from live-matrix.toml: what Anvil's live suites run on this platform.\n")
				append("[verified]\n")
				versions.forEach { (version, java) -> append("\"$version\" = [$java]\n") }
			}
		}
	}
}
