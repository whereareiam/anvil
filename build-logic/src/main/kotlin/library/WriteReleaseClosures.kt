package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

/**
 * Writes where the locked runtime closure of every release of one library lies on this machine, so tests can start
 * real workers without downloading releases: a properties file with one `<release version>=<JARs>` entry per
 * release, its JARs in classpath order joined with the platform's path separator.
 *
 * Tests receive the file's location instead of the paths themselves, which keeps the test JVM's command line short.
 * The entries are sorted and carry no timestamp, so the file changes only when a closure does.
 */
abstract class WriteReleaseClosures : DefaultTask() {
	/**
	 * The locked closure of each release.
	 */
	@get:Nested
	abstract val releases: ListProperty<ReleaseClasspath>

	/**
	 * The properties file.
	 */
	@get:OutputFile
	abstract val destination: RegularFileProperty

	@TaskAction
	fun write() {
		val closures = releases.get().associate { release ->
			release.version.get() to release.files.files.joinToString(File.pathSeparator) { it.absolutePath }
		}
		destination.get().asFile.writeText(closureFile(closures), Charsets.ISO_8859_1)
	}

	internal companion object {
		/**
		 * Formats closures as the sorted entries of a properties file without comments. Storing to a stream escapes
		 * keys and paths to ASCII, which `Properties.load(InputStream)` reads back whatever the machine's encoding;
		 * the leading timestamp comment would change the file on every run.
		 */
		fun closureFile(closures: Map<String, String>): String {
			val properties = Properties().apply { putAll(closures) }
			return ByteArrayOutputStream().also { properties.store(it, null) }.toString(Charsets.ISO_8859_1).lineSequence()
				.filter { it.isNotEmpty() && !it.startsWith("#") }
				.sorted()
				.joinToString("") { "$it\n" }
		}
	}
}
