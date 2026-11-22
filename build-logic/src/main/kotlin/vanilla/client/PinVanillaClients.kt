package me.whereareiam.anvil.buildlogic.vanilla.client

import com.fasterxml.jackson.databind.ObjectMapper
import me.whereareiam.anvil.buildlogic.download.Checksums
import me.whereareiam.anvil.buildlogic.download.HttpReader
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.IOException
import java.net.URI
import java.time.Duration

/**
 * Pins the vanilla client of every release in `vanilla-clients.toml`, `pinVanillaClients`.
 *
 * The task reads Mojang's version manifest, which names each version's metadata by URL and SHA-1, reads each
 * release's metadata and pins it with the client JAR and, when Mojang ships the client obfuscated, its mappings. A
 * pinned row never changes silently: when Mojang now serves another metadata, client or mappings for it, the task
 * fails before anything is written.
 */
@UntrackedTask(because = "It rewrites the pins file in place from what Mojang serves")
abstract class PinVanillaClients : DefaultTask() {
	/**
	 * The pins file, read and rewritten in place.
	 */
	@get:Internal
	abstract val pins: RegularFileProperty

	@TaskAction
	fun pin() {
		val file = pins.get().asFile
		val current = VanillaClientPinsFile.read(file)
		val json = ObjectMapper()
		val pinned = HttpReader().use { http ->
			val manifest = json.readTree(read(http, current.manifest, null))
			val versions = manifest.path("versions").associateBy { it.path("id").asText() }
			current.releases.map { release ->
				val entry = versions[release.minecraft]
					?: throw GradleException("Mojang's version manifest lists no Minecraft ${release.minecraft}")
				val metadata = VanillaClientPins.PinnedFile(entry.path("url").asText(), entry.path("sha1").asText())
				val parsed = VersionMetadata.parse(json.readTree(read(http, metadata.url, metadata.sha1)), "${release.minecraft}.json")
				release.copy(
					metadata = metadata,
					client = VanillaClientPins.PinnedFile(parsed.client.url, parsed.client.sha1),
					mappings = parsed.mappings?.let { VanillaClientPins.PinnedFile(it.url, it.sha1) },
				)
			}
		}
		val changes = current.releases.zip(pinned).filter { (old, new) -> old.pinned && old != new }
		if (changes.isNotEmpty())
			throw GradleException("Mojang now serves other files for releases ${VanillaClientPinsFile.NAME} already pins; pins never "
				+ "change silently, so check them and clear a row only on purpose:\n"
				+ changes.joinToString("\n") { (old, new) -> "  ${old.minecraft}: $old -> $new" })

		file.writeText(VanillaClientPinsFile.format(current.copy(releases = pinned)))
		logger.lifecycle("Pinned the vanilla clients of ${pinned.joinToString { it.minecraft }}")
	}

	private fun read(http: HttpReader, url: String, sha1: String?): ByteArray = try {
		http.get(URI(url), Duration.ofMinutes(2)) { status, body ->
			if (status != 200) throw GradleException("Mojang does not serve $url: it answered $status")
			val bytes = body.readAllBytes()
			if (sha1 != null) {
				val actual = Checksums.sha1(bytes.inputStream())
				if (actual != sha1) throw GradleException("$url has SHA-1 $actual, but the manifest names $sha1")
			}
			bytes
		}
	} catch (exception: IOException) {
		throw GradleException("Could not read $url: ${exception.message}", exception)
	}
}
