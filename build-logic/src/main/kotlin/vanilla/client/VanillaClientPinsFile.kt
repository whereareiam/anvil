package me.whereareiam.anvil.buildlogic.vanilla.client

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException
import java.io.File

/**
 * Reads and writes `vanilla-clients.toml`, the pins of the vanilla clients that `generateVanillaData` runs.
 *
 * The file has one `[source]` table naming Mojang's version `manifest`, and one `[[release]]` row per Minecraft
 * release: its `minecraft` version, the `harness` family that runs it, and, once `pinVanillaClients` ran, one
 * `{ url, sha1 }` table each for the version `metadata`, the `client` JAR and, for a client Mojang ships obfuscated,
 * its `mappings`. Versions are unique across rows. The pin task owns everything but the manifest, each row's
 * `minecraft` and its `harness`, and writes the file in the layout of [format].
 */
object VanillaClientPinsFile {
	/**
	 * The file's name in the harness build.
	 */
	const val NAME = "vanilla-clients.toml"

	private val fields = listOf("metadata", "client", "mappings")
	private val sha1 = Regex("""[0-9a-f]{40}""")
	private val version = Regex("""\d+(\.\d+)+""")
	private val harness = Regex("""v\d+(_\d+)*""")

	/**
	 * Reads and validates [file].
	 *
	 * @throws GradleException when the file cannot be parsed or violates the layout above
	 */
	fun read(file: File): VanillaClientPins = parse(file.readText())

	/**
	 * Reads and validates the text of the pins file.
	 *
	 * @throws GradleException when the text cannot be parsed or violates the layout above
	 */
	fun parse(text: String): VanillaClientPins {
		val document = try {
			TomlMapper().readTree(text)
		} catch (exception: JacksonException) {
			throw GradleException("$NAME is not valid TOML: ${exception.originalMessage}", exception)
		}
		known(document, setOf("source", "release"), NAME)
		val source = document.path("source")
		if (!source.isObject) fail("must declare a [source] table")
		known(source, setOf("manifest"), "$NAME [source]")
		val manifest = text(source, "manifest", "[source]")
		if (!manifest.startsWith("https://")) fail("[source] manifest must be an https URL")

		val rows = document.path("release")
		if (!rows.isArray || rows.isEmpty) fail("must declare at least one [[release]]")
		val releases = rows.mapIndexed { index, row -> release(row, "release #${index + 1}") }
		releases.groupingBy { it.minecraft }.eachCount().filterValues { it > 1 }.keys
			.forEach { fail("lists Minecraft $it more than once") }

		return VanillaClientPins(manifest, releases)
	}

	/**
	 * Formats [pins] as the file's content, with the explaining header, rows in their order.
	 */
	fun format(pins: VanillaClientPins): String = buildString {
		append(HEADER)
		append("\n[source]\n")
		append("manifest = \"${pins.manifest}\"\n")
		pins.releases.forEach { release ->
			append("\n[[release]]\n")
			append("minecraft = \"${release.minecraft}\"\n")
			append("harness = \"${release.harness}\"\n")
			release.metadata?.let { append("metadata = ${table(it)}\n") }
			release.client?.let { append("client = ${table(it)}\n") }
			release.mappings?.let { append("mappings = ${table(it)}\n") }
		}
	}

	private fun table(file: VanillaClientPins.PinnedFile): String = "{ url = \"${file.url}\", sha1 = \"${file.sha1}\" }"

	private fun release(row: JsonNode, position: String): VanillaClientPins.Release {
		if (!row.isObject) fail("$position must be a table")
		known(row, setOf("minecraft", "harness") + fields, "$NAME $position")
		val minecraft = text(row, "minecraft", position)
		if (!version.matches(minecraft)) fail("$position minecraft '$minecraft' is not a version")
		val context = "Minecraft $minecraft"
		val family = text(row, "harness", context)
		if (!harness.matches(family)) fail("$context harness '$family' must name a family source set such as v1_21_11")

		val metadata = file(row, "metadata", context)
		val client = file(row, "client", context)
		val mappings = file(row, "mappings", context)
		if ((metadata == null) != (client == null)) fail("$context must pin metadata and client together, or neither")
		if (mappings != null && client == null) fail("$context pins mappings without a client")

		return VanillaClientPins.Release(minecraft, family, metadata, client, mappings)
	}

	private fun file(row: JsonNode, field: String, context: String): VanillaClientPins.PinnedFile? {
		val table = row.path(field)
		if (table.isMissingNode) return null
		if (!table.isObject) fail("$context $field must be a { url, sha1 } table")
		known(table, setOf("url", "sha1"), "$NAME $context $field")
		val url = text(table, "url", "$context $field")
		if (!url.startsWith("https://")) fail("$context $field url must be an https URL")
		val checksum = text(table, "sha1", "$context $field")
		if (!sha1.matches(checksum)) fail("$context $field sha1 must be 40 lower-case hex digits")

		return VanillaClientPins.PinnedFile(url, checksum)
	}

	private fun text(node: JsonNode, field: String, context: String): String {
		val value = node.path(field)
		if (!value.isTextual || value.asText().isBlank()) fail("$context must declare a non-blank $field")
		return value.asText()
	}

	private fun known(node: JsonNode, known: Set<String>, context: String) {
		val unknown = node.fieldNames().asSequence().filterNot(known::contains).toList()
		if (unknown.isNotEmpty()) throw GradleException("$context has unknown fields $unknown; expected $known")
	}

	private fun fail(message: String): Nothing = throw GradleException("$NAME: $message")

	private val HEADER = """
		# The vanilla clients that generateVanillaData runs headless to read reference data from Minecraft itself, such as
		# the per-tick physics traces of anvil-testkit/physics/traces. Each [[release]] names a Minecraft version and the
		# harness family (a source set of this build) that runs it, and pins Mojang's version metadata, client JAR and, for
		# a client Mojang ships obfuscated, its mappings, each by URL and SHA-1. The metadata pins every library the client
		# runs with by SHA-1 in turn. generateVanillaData downloads them into ~/.anvil/vanilla-clients, never into the
		# repository; only what it generates is committed.
		# Add a row with its minecraft and harness, then run
		# ./gradlew -p anvil-testkit/physics/harness pinVanillaClients
		# which finds each release in [source] manifest and pins it. Pins never change silently: a pinned row whose files
		# Mojang now serves differently fails the task; clear it only on purpose.
	""".trimIndent() + "\n"
}
