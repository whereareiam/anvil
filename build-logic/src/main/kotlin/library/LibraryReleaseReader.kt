package me.whereareiam.anvil.buildlogic.library

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException
import java.io.File

/**
 * Reads and validates a protocol library's releases file (`<library>-releases.toml`).
 *
 * Each `[[release]]` row declares `version`, `edition`, `module`, `protocol`, `minecraft`, `verified`, `java`,
 * `features` and a non-empty `[[release.artifact]]` closure of `module`, `url` and `sha256`. `edition` is an opaque
 * lower-case identifier; `protocol` is either one number every listed version speaks or a `[release.protocol]`
 * table mapping each listed version to its own number. Release versions and release keys are unique across rows, a
 * Minecraft version belongs to at most one release of its edition, verified versions are listed in `minecraft`, the
 * release's own module is part of its closure, and a checksum is either empty (not pinned yet) or 64 lower-case hex
 * digits. A release
 * module is a `group:name:version` coordinate; an artifact module may add a classifier, as native transport
 * JARs do.
 */
object LibraryReleaseReader {
	private val rowFields = setOf("version", "edition", "module", "protocol", "minecraft", "verified", "java", "features", "artifact")
	private val artifactFields = setOf("module", "url", "sha256")
	private val coordinate = Regex("""[^:\s]+:[^:\s]+:[^:\s]+""")
	private val classifiedCoordinate = Regex("""[^:\s]+:[^:\s]+:[^:\s]+(:[^:\s]+)?""")
	private val checksum = Regex("""[0-9a-f]{64}""")
	private val edition = Regex("""[a-z][a-z0-9-]*""")
	private const val minimumJava = 8

	/**
	 * Reads every release of [file], ordered by release key.
	 *
	 * @throws GradleException when the file cannot be parsed or violates the schema
	 */
	fun read(file: File): List<LibraryRelease> {
		val document = try {
			TomlMapper().readTree(file)
		} catch (exception: JacksonException) {
			throw GradleException("${file.name} is not valid TOML: ${exception.originalMessage}", exception)
		}
		return Rows(file.name).read(document)
	}

	private class Rows(private val source: String) {
		fun read(document: JsonNode): List<LibraryRelease> {
			unknownFields(document, setOf("release"), source)
			val rows = document.path("release")
			if (!rows.isArray || rows.isEmpty) fail(source, "must declare at least one [[release]]")

			val releases = rows.mapIndexed { index, row -> release(row, "release #${index + 1}") }
			duplicates(releases.map(LibraryRelease::version)).forEach { fail(source, "declares release $it more than once") }
			duplicates(releases.flatMap { release -> release.minecraft.map { release.edition to it } }).forEach { (edition, version) ->
				fail(source, "assigns $edition Minecraft $version to more than one release")
			}
			duplicates(releases.map(LibraryRelease::key)).forEach { fail(source, "keys more than one release by Minecraft $it") }
			return releases.sortedBy(LibraryRelease::key)
		}

		private fun release(row: JsonNode, position: String): LibraryRelease {
			if (!row.isObject) fail(source, "$position must be a table")
			val version = text(row, "version", position)
			val context = "release $version"
			unknownFields(row, rowFields, "$source $context")

			val minecraft = versions(row, "minecraft", context)
			if (minecraft.isEmpty()) fail(source, "$context must list at least one Minecraft version")
			val verified = versions(row, "verified", context)
			verified.filterNot(minecraft::contains).forEach { fail(source, "$context verifies Minecraft $it, which it does not list") }

			val module = module(row, "module", context, classified = false)
			val artifacts = artifacts(row.path("artifact"), context)
			if (artifacts.none { it.module == module }) fail(source, "$context must include its module $module in its artifacts")

			return LibraryRelease(
				version = version,
				edition = edition(row, context),
				module = module,
				protocols = protocols(row, minecraft, context),
				minecraft = minecraft,
				verified = verified,
				java = number(row, "java", context, minimum = minimumJava),
				features = strings(row, "features", context, required = false),
				artifacts = artifacts,
			)
		}

		private fun edition(row: JsonNode, context: String): String {
			val value = text(row, "edition", context)
			if (!edition.matches(value)) fail(source, "$context edition '$value' must be one lower-case identifier")
			return value
		}

		/**
		 * Reads the protocol number of every listed version: one number shared by all of them, or a table with one
		 * number per listed version.
		 */
		private fun protocols(row: JsonNode, minecraft: List<MinecraftVersion>, context: String): Map<MinecraftVersion, Int> {
			val protocol = row.path("protocol")
			if (!protocol.isObject) {
				val shared = number(row, "protocol", context, minimum = 1)
				return minecraft.associateWith { shared }
			}

			val protocols = linkedMapOf<MinecraftVersion, Int>()
			protocol.fieldNames().forEach { key ->
				val version = version(key, "$context protocol")
				if (version !in minecraft) fail(source, "$context maps protocol of Minecraft $version, which it does not list")
				if (version in protocols) fail(source, "$context maps protocol of Minecraft $version more than once")
				protocols[version] = number(protocol, key, "$context protocol", minimum = 1)
			}
			minecraft.filterNot(protocols::containsKey).forEach { fail(source, "$context maps no protocol for Minecraft $it") }
			return protocols
		}

		private fun artifacts(rows: JsonNode, context: String): List<ReleaseArtifact> {
			if (!rows.isArray || rows.isEmpty) fail(source, "$context must declare its runtime closure as [[release.artifact]]")
			val artifacts = rows.map { row ->
				if (!row.isObject) fail(source, "$context artifacts must be tables")
				val module = module(row, "module", "$context artifact", classified = true)
				unknownFields(row, artifactFields, "$source $context artifact $module")
				val sha256 = row.path("sha256")
				if (!sha256.isTextual || !(sha256.asText().isEmpty() || checksum.matches(sha256.asText())))
					fail(source, "$context artifact $module must have an empty or 64-digit lower-case hex sha256")
				ReleaseArtifact(module, text(row, "url", "$context artifact $module"), sha256.asText())
			}
			duplicates(artifacts.map(ReleaseArtifact::module)).forEach { fail(source, "$context lists artifact $it more than once") }
			return artifacts
		}

		private fun module(row: JsonNode, field: String, context: String, classified: Boolean): String {
			val value = text(row, field, context)
			if (classified && !classifiedCoordinate.matches(value))
				fail(source, "$context $field '$value' must be a group:name:version[:classifier] coordinate")
			if (!classified && !coordinate.matches(value))
				fail(source, "$context $field '$value' must be a group:name:version coordinate")
			return value
		}

		private fun text(row: JsonNode, field: String, context: String): String {
			val value = row.path(field)
			if (!value.isTextual || value.asText().isBlank()) fail(source, "$context must declare a non-blank $field")
			return value.asText()
		}

		private fun number(row: JsonNode, field: String, context: String, minimum: Int): Int {
			val value = row.path(field)
			if (!value.isIntegralNumber || !value.canConvertToInt() || value.asInt() < minimum)
				fail(source, "$context must declare $field as an integer of at least $minimum")
			return value.asInt()
		}

		private fun versions(row: JsonNode, field: String, context: String): List<MinecraftVersion> {
			val versions = strings(row, field, context, required = true).map { version(it, "$context $field") }
			duplicates(versions).forEach { fail(source, "$context lists $field $it more than once") }
			return versions
		}

		private fun version(text: String, context: String): MinecraftVersion =
			try {
				MinecraftVersion.parse(text)
			} catch (exception: IllegalArgumentException) {
				fail(source, "$context: ${exception.message}")
			}

		private fun strings(row: JsonNode, field: String, context: String, required: Boolean): List<String> {
			val value = row.path(field)
			if (value.isMissingNode && !required) return emptyList()
			if (!value.isArray || value.any { !it.isTextual || it.asText().isBlank() })
				fail(source, "$context must declare $field as an array of strings")
			return value.map(JsonNode::asText)
		}
	}

	private fun unknownFields(node: JsonNode, known: Set<String>, context: String) {
		val unknown = node.fieldNames().asSequence().filterNot(known::contains).toList()
		if (unknown.isNotEmpty()) throw GradleException("$context has unknown fields $unknown; expected $known")
	}

	private fun <T> duplicates(values: List<T>): Set<T> = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

	private fun fail(source: String, message: String): Nothing = throw GradleException("$source: $message")
}
