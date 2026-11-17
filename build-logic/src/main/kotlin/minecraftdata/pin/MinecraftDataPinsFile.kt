package me.whereareiam.anvil.buildlogic.minecraftdata.pin

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import me.whereareiam.anvil.buildlogic.minecraftdata.MinecraftDataKind
import org.gradle.api.GradleException
import java.io.File

/**
 * Reads and writes the pins file of one [MinecraftDataKind], such as `block-data.toml`.
 *
 * The file has one `[source]` table naming the minecraft-data `repository`, its release tag `version` and the
 * `commit` of that tag, and one `[[release]]` row per protocol: its `protocol` number and, when minecraft-data has the
 * kind's data, one `{ path, sha256 }` table per file of the kind, under the file's field. Protocols are unique across
 * rows, and a row pins every file of the kind or none. The kind's pin task owns everything but the `repository`, the
 * `version` and each row's `protocol`, and writes the file in the layout of [format].
 *
 * @param kind the kind the file pins
 */
class MinecraftDataPinsFile(private val kind: MinecraftDataKind) {
	private val fields = kind.files.map { it.field }

	/**
	 * Reads and validates [file].
	 *
	 * @throws GradleException when the file cannot be parsed or violates the schema
	 */
	fun read(file: File): MinecraftDataPins {
		val document = try {
			TomlMapper().readTree(file)
		} catch (exception: JacksonException) {
			throw GradleException("${file.name} is not valid TOML: ${exception.originalMessage}", exception)
		}
		return Document(file.name).read(document)
	}

	/**
	 * Formats [pins] as the file's content, with the explaining header and rows ordered by protocol.
	 *
	 * @param pinTask the path of the kind's pin task, which the header tells how to run
	 */
	fun format(pins: MinecraftDataPins, pinTask: String): String = buildString {
		append(header(pinTask))
		append("\n[source]\n")
		append("repository = \"${pins.source.repository}\"\n")
		append("version = \"${pins.source.version}\"\n")
		append("commit = \"${pins.source.commit}\"\n")
		pins.releases.sortedBy { it.protocol }.forEach { release ->
			append("\n[[release]]\n")
			append("protocol = ${release.protocol}\n")
			fields.forEach { field -> release.files[field]?.let { append("$field = ${table(it)}\n") } }
		}
	}

	private fun table(file: MinecraftDataPins.PinnedFile): String = "{ path = \"${file.path}\", sha256 = \"${file.sha256}\" }"

	private fun header(pinTask: String): String = """
		# ${kind.subject.replaceFirstChar(Char::uppercase)} of every Minecraft release Anvil supports, generated at build time from
		# PrismarineJS minecraft-data (https://github.com/PrismarineJS/minecraft-data, MIT). Each [[release]] names one
		# protocol, whose Minecraft versions share one set of ${kind.subject}, and pins the minecraft-data files of those
		# versions (${kind.files.joinToString { "${it.entry}.json" }}), which ${pinTask.substringAfterLast(':')} finds through
		# minecraft-data's own data/pc/common/protocolVersions.json and data/dataPaths.json. A row without files has no
		# ${kind.subject}: minecraft-data has none for any version of its protocol. check${kind.stem}Releases requires one
		# row for every protocol a protocol library release speaks.
		# Change [source] version to another minecraft-data release, or add a row, then run
		# ./gradlew $pinTask
		# which resolves the commit and rewrites every pin. Pins never change silently while the commit stays the same.
	""".trimIndent() + "\n"

	private inner class Document(private val source: String) {
		fun read(document: JsonNode): MinecraftDataPins {
			unknownFields(document, setOf("source", "release"), source)
			val table = document.path("source")
			if (!table.isObject) fail("must declare a [source] table")
			unknownFields(table, setOf("repository", "version", "commit"), "$source [source]")
			val repositoryName = text(table, "repository", "[source]")
			if (!REPOSITORY.matches(repositoryName)) fail("[source] repository '$repositoryName' must be owner/name")
			val commitId = table.path("commit").asText("")
			if (commitId.isNotEmpty() && !COMMIT.matches(commitId)) fail("[source] commit must be empty or 40 lower-case hex digits")

			val rows = document.path("release")
			if (!rows.isArray || rows.isEmpty) fail("must declare at least one [[release]]")
			val releases = rows.mapIndexed { index, row -> release(row, "release #${index + 1}") }
			releases.groupingBy { it.protocol }.eachCount().filterValues { it > 1 }.keys
				.forEach { fail("lists protocol $it more than once") }

			return MinecraftDataPins(
				MinecraftDataPins.Source(repositoryName, text(table, "version", "[source]"), commitId),
				releases.sortedBy { it.protocol },
			)
		}

		private fun release(row: JsonNode, position: String): MinecraftDataPins.Release {
			if (!row.isObject) fail("$position must be a table")
			unknownFields(row, setOf("protocol") + fields, "$source $position")
			val protocol = row.path("protocol")
			if (!protocol.isIntegralNumber || protocol.asInt() < 1) fail("$position must name its protocol as a positive integer")
			val context = "protocol ${protocol.asInt()}"
			val files = fields.mapNotNull { field -> file(row, field, context)?.let { field to it } }.toMap()
			if (files.isNotEmpty() && files.size != fields.size)
				fail("$context must pin ${fields.joinToString(" and ")}, or none of them")

			return MinecraftDataPins.Release(protocol.asInt(), files)
		}

		private fun file(row: JsonNode, field: String, context: String): MinecraftDataPins.PinnedFile? {
			val table = row.path(field)
			if (table.isMissingNode) return null
			if (!table.isObject) fail("$context $field must be a { path, sha256 } table")
			unknownFields(table, setOf("path", "sha256"), "$source $context $field")
			val sha256 = text(table, "sha256", "$context $field")
			if (!CHECKSUM.matches(sha256)) fail("$context $field sha256 must be 64 lower-case hex digits")

			val path = text(table, "path", "$context $field")
			if (!MinecraftDataPins.PinnedFile.PATH.matches(path)) fail("$context $field path '$path' must be data/pc/<directory>/<name>.json")

			return MinecraftDataPins.PinnedFile(path, sha256)
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

	private companion object {
		val CHECKSUM = Regex("""[0-9a-f]{64}""")
		val COMMIT = Regex("""[0-9a-f]{40}""")
		val REPOSITORY = Regex("""[\w.-]+/[\w.-]+""")
	}
}
