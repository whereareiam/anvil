package me.whereareiam.anvil.buildlogic.matrix

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException
import java.io.File

/**
 * Anvil's live matrix, `live-matrix.toml` at the repository root: what the live suites run against real servers, and
 * the only list of what Anvil calls verified.
 *
 * Each top-level table is one platform, named by its provider's id: an array of tables for platforms with several
 * rows, such as `[[paper]]`, or one table, such as `[velocity]`. A row names its platform version as `minecraft`
 * (servers) or `version` (proxies) and the `java` the live suites run it on; its other fields, such as a pinned
 * build, belong to the scenarios. A row without `java` verifies nothing, as BungeeCord's, whose builds carry no
 * release version.
 *
 * @property platforms the rows of each platform, in the file's order
 */
data class LiveMatrix(val platforms: Map<String, List<Row>>) {
	/**
	 * One combination the live suites run.
	 *
	 * @property version the platform version, or `null` for a row that names none
	 * @property server whether the version is a Minecraft version (`minecraft`) rather than a proxy's own (`version`)
	 * @property java the Java feature version the suites run it on, or `null` for a row that verifies nothing
	 */
	data class Row(val version: String?, val server: Boolean, val java: Int?)

	/**
	 * The versions of [platform] and the Java each is verified on, in the file's order; empty for a platform the
	 * matrix does not run.
	 */
	fun verified(platform: String): Map<String, Int> = platforms[platform].orEmpty()
		.filter { it.version != null && it.java != null }
		.associate { it.version!! to it.java!! }

	/**
	 * The Minecraft versions of [platform]'s rows, in the file's order, such as the Paper servers the release suite
	 * runs one task each for.
	 */
	fun minecraftVersions(platform: String): List<String> = platforms[platform].orEmpty()
		.filter { it.server }
		.mapNotNull { it.version }

	/**
	 * Every Minecraft version a server of any platform runs, which the protocol libraries' players join.
	 */
	val serverVersions: Set<String>
		get() = platforms.keys.flatMap(::minecraftVersions).toSet()

	companion object {
		private val version = Regex("""\d+(\.\d+)+""")

		/**
		 * Reads and validates [file].
		 *
		 * @throws GradleException when the file is not valid TOML or violates the layout above
		 */
		fun read(file: File): LiveMatrix = parse(file.readText())

		/**
		 * Reads and validates the text of `live-matrix.toml`.
		 *
		 * @throws GradleException when the text is not valid TOML or violates the layout above
		 */
		fun parse(text: String): LiveMatrix {
			val document = try {
				TomlMapper().readTree(text)
			} catch (exception: JacksonException) {
				throw GradleException("live-matrix.toml is not valid TOML: ${exception.originalMessage}", exception)
			}
			if (document.isEmpty) throw GradleException("live-matrix.toml lists no platform")

			val platforms = document.properties().associate { (platform, rows) -> platform to rows(platform, rows) }
			return LiveMatrix(platforms)
		}

		private fun rows(platform: String, node: JsonNode): List<Row> {
			val tables = if (node.isArray) node.toList() else listOf(node)
			if (tables.isEmpty() || tables.any { !it.isObject }) fail("[$platform] must be a table or an array of tables")

			val rows = tables.map { row(platform, it) }
			rows.mapNotNull { it.version }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
				.forEach { fail("[$platform] lists $it more than once") }
			return rows
		}

		private fun row(platform: String, table: JsonNode): Row {
			if (table.has("minecraft") && table.has("version")) fail("a [$platform] row names both minecraft and version")
			val field = if (table.has("version")) "version" else "minecraft"
			val text = table.path(field).takeUnless(JsonNode::isMissingNode)?.let { value ->
				if (!value.isTextual || !version.matches(value.asText()))
					fail("a [$platform] row's $field '$value' is not a version")
				value.asText()
			}
			val java = table.path("java").takeUnless(JsonNode::isMissingNode)?.let { value ->
				if (!value.isIntegralNumber || value.asInt() < 1)
					fail("[$platform] $text's java must be a Java feature version")
				value.asInt()
			}
			if (java != null && text == null) fail("a [$platform] row names java but no minecraft or version it verifies")

			return Row(text, field == "minecraft" && text != null, java)
		}

		private fun fail(message: String): Nothing = throw GradleException("live-matrix.toml: $message")
	}
}
