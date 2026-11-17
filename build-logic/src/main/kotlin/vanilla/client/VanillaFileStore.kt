package me.whereareiam.anvil.buildlogic.vanilla.client

import me.whereareiam.anvil.buildlogic.download.Checksums
import me.whereareiam.anvil.buildlogic.download.HttpReader
import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * Downloads Mojang's files into the vanilla client cache, each verified against the SHA-1 its pin or the pinned
 * version metadata names. A file already in the cache is reused only while its bytes still match; a download lands
 * beside its target and moves into place once verified, so the cache never holds a partial or unverified file, also
 * when several builds fill it at once.
 */
class VanillaFileStore : AutoCloseable {
	private val http = HttpReader()

	/**
	 * Makes [target] hold the bytes [url] serves whose SHA-1 is [sha1].
	 *
	 * @return [target]
	 * @throws GradleException when the file cannot be downloaded or its checksum differs
	 */
	fun fetch(url: String, sha1: String, target: File): File {
		if (target.isFile && Checksums.sha1(target) == sha1) return target
		target.parentFile.mkdirs()
		val partial = File.createTempFile(target.name, ".part", target.parentFile)
		try {
			http.get(URI(url), Duration.ofMinutes(10)) { status, body ->
				if (status != 200) throw GradleException("Mojang does not serve $url: it answered $status")
				partial.outputStream().use { body.copyTo(it) }
			}
			val actual = Checksums.sha1(partial)
			if (actual != sha1) throw GradleException("$url has SHA-1 $actual, but its pin names $sha1")
			Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
			return target
		} catch (exception: IOException) {
			throw GradleException("Could not download $url: ${exception.message}", exception)
		} finally {
			partial.delete()
		}
	}

	override fun close() = http.close()
}
