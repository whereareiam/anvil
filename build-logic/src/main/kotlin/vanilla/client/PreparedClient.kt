package me.whereareiam.anvil.buildlogic.vanilla.client

import org.gradle.api.GradleException
import java.io.File

/**
 * What `prepareVanillaClient-<minecraft>` leaves in its output directory for the tasks that compile and run a family
 * harness against the client: the platform the client runs on and its class path there, the libraries first and the
 * client JAR with Mojang's names last, as Mojang's launcher orders them.
 *
 * @property platform where the client runs: the host's platform, or [ClientPlatform.CONTAINER] when Mojang ships no
 * natives for the host
 * @property classpath the class path entries in order
 */
data class PreparedClient(val platform: ClientPlatform, val classpath: List<File>) {
	/**
	 * Writes the prepared client into [directory].
	 */
	fun write(directory: File) {
		directory.mkdirs()
		File(directory, PLATFORM).writeText("${platform.os} ${platform.arch}\n")
		File(directory, CLASSPATH).writeText(classpath.joinToString("") { "${it.absolutePath}\n" })
	}

	companion object {
		private const val PLATFORM = "platform.txt"
		private const val CLASSPATH = "classpath.txt"

		/**
		 * Reads the class path entries a prepared client in [directory] lists, without reading its platform.
		 */
		fun classpath(directory: File): List<File> = File(directory, CLASSPATH).readLines().filter(String::isNotBlank).map(::File)

		/**
		 * Reads the prepared client in [directory].
		 *
		 * @throws GradleException when the directory holds no prepared client
		 */
		fun read(directory: File): PreparedClient {
			val platform = File(directory, PLATFORM)
			if (!platform.isFile) throw GradleException("$directory holds no prepared vanilla client")
			val (os, arch) = platform.readText().trim().split(' ')
			return PreparedClient(ClientPlatform(os, arch), classpath(directory))
		}
	}
}
