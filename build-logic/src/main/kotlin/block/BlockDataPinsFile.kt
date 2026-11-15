package me.whereareiam.anvil.buildlogic.block

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException
import java.io.File

/**
 * Reads and writes `block-data.toml`, the pins of the block data that `protocol-java-block` generates.
 *
 * The file has one `[source]` table naming the minecraft-data `repository`, its release tag `version` and the
 * `commit` of that tag, and one `[[release]]` row per protocol: its `protocol` number and, when minecraft-data has its
 * block data, `blocks` and `collision-shapes` as `{ path, sha256 }` tables. Protocols are unique across rows, and a
 * row pins both files or neither. `pinBlockData` owns everything but the `repository`, the `version` and each row's
 * `protocol`, and writes the file in the layout of [format].
 */
object BlockDataPinsFile {
	private val checksum = Regex("""[0-9a-f]{64}""")
	private val commit = Regex("""[0-9a-f]{40}""")
	private val repository = Regex("""[\w.-]+/[\w.-]+""")

	/**
	 * Reads and validates [file].
	 *
	 * @throws GradleException when the file cannot be parsed or violates the schema
	 */
	fun read(file: File): BlockDataPins {
		val document = try {
			TomlMapper().readTree(file)
		} catch (exception: JacksonException) {
			throw GradleException("${file.name} is not valid TOML: ${exception.originalMessage}", exception)
		}
		return Document(file.name).read(document)
	}

	/**
	 * Formats [pins] as the content of `block-data.toml`, with the explaining header and rows ordered by protocol.
	 */
	fun format(pins: BlockDataPins): String = buildString {
		append(header)
		append("\n[source]\n")
		append("repository = \"${pins.source.repository}\"\n")
		append("version = \"${pins.source.version}\"\n")
		append("commit = \"${pins.source.commit}\"\n")
		pins.releases.sortedBy { it.protocol }.forEach { release ->
			append("\n[[release]]\n")
			append("protocol = ${release.protocol}\n")
			release.blocks?.let { append("blocks = ${table(it)}\n") }
			release.collisionShapes?.let { append("collision-shapes = ${table(it)}\n") }
		}
	}

	private fun table(file: BlockDataPins.PinnedFile): String = "{ path = \"${file.path}\", sha256 = \"${file.sha256}\" }"

	private val header = """
		# Block data of every Minecraft release Anvil supports, generated at build time from PrismarineJS minecraft-data
		# (https://github.com/PrismarineJS/minecraft-data, MIT). Each [[release]] names one protocol, whose Minecraft
		# versions share one block registry, and pins the minecraft-data files of those versions, which pinBlockData finds
		# through minecraft-data's own data/pc/common/protocolVersions.json and data/dataPaths.json. A row without files has
		# no block data: minecraft-data has none for any version of its protocol. checkBlockDataReleases requires one row
		# for every protocol a protocol library release speaks.
		# Change [source] version to another minecraft-data release, or add a row, then run
		# ./gradlew :anvil-player:player-protocol:protocol-java:protocol-java-block:pinBlockData, which resolves the
		# commit and rewrites every pin. Pins never change silently while the commit stays the same.
	""".trimIndent() + "\n"

	private class Document(private val source: String) {
		fun read(document: JsonNode): BlockDataPins {
			unknownFields(document, setOf("source", "release"), source)
			val table = document.path("source")
			if (!table.isObject) fail("must declare a [source] table")
			unknownFields(table, setOf("repository", "version", "commit"), "$source [source]")
			val repositoryName = text(table, "repository", "[source]")
			if (!repository.matches(repositoryName)) fail("[source] repository '$repositoryName' must be owner/name")
			val commitId = table.path("commit").asText("")
			if (commitId.isNotEmpty() && !commit.matches(commitId)) fail("[source] commit must be empty or 40 lower-case hex digits")

			val rows = document.path("release")
			if (!rows.isArray || rows.isEmpty) fail("must declare at least one [[release]]")
			val releases = rows.mapIndexed { index, row -> release(row, "release #${index + 1}") }
			releases.groupingBy { it.protocol }.eachCount().filterValues { it > 1 }.keys
				.forEach { fail("lists protocol $it more than once") }

			return BlockDataPins(
				BlockDataPins.Source(repositoryName, text(table, "version", "[source]"), commitId),
				releases.sortedBy { it.protocol },
			)
		}

		private fun release(row: JsonNode, position: String): BlockDataPins.Release {
			if (!row.isObject) fail("$position must be a table")
			unknownFields(row, setOf("protocol", "blocks", "collision-shapes"), "$source $position")
			val protocol = row.path("protocol")
			if (!protocol.isIntegralNumber || protocol.asInt() < 1) fail("$position must name its protocol as a positive integer")
			val context = "protocol ${protocol.asInt()}"
			val blocks = file(row, "blocks", context)
			val collisionShapes = file(row, "collision-shapes", context)
			if ((blocks == null) != (collisionShapes == null)) fail("$context must pin both blocks and collision-shapes, or neither")

			return BlockDataPins.Release(protocol.asInt(), blocks, collisionShapes)
		}

		private fun file(row: JsonNode, field: String, context: String): BlockDataPins.PinnedFile? {
			val table = row.path(field)
			if (table.isMissingNode) return null
			if (!table.isObject) fail("$context $field must be a { path, sha256 } table")
			unknownFields(table, setOf("path", "sha256"), "$source $context $field")
			val sha256 = text(table, "sha256", "$context $field")
			if (!checksum.matches(sha256)) fail("$context $field sha256 must be 64 lower-case hex digits")

			val path = text(table, "path", "$context $field")
			if (!BlockDataPins.PinnedFile.PATH.matches(path)) fail("$context $field path '$path' must be data/pc/<directory>/<name>.json")

			return BlockDataPins.PinnedFile(path, sha256)
		}

		private fun text(node: JsonNode, field: String, context: String): String {
			val value = node.path(field)
			if (!value.isTextual || value.asText().isBlank()) fail("$context must declare a non-blank $field")
			return value.asText()
		}

		private fun unknownFields(node: JsonNode, known: Set<String>, context: String) {
			val unknown = node.fieldNames().asSequence().filterNot(known::contains).toList()
			if (unknown.isNotEmpty()) throw GradleException("$context has unknown fields $unknown; expected $known")
		}

		private fun fail(message: String): Nothing = throw GradleException("$source: $message")
	}
}
