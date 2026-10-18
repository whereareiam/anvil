package me.whereareiam.anvil.buildlogic.data

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException
import java.io.File

/**
 * Reads `block-facts.toml`, the block facts that minecraft-data does not carry.
 *
 * Each `[[block]]` entry lists namespaced block `names`, the vanilla `source` its values come from, and any of
 * `friction`, `speed-factor`, `jump-factor`, `bounce`, `stuck = { horizontal, vertical }`, `fluid` (`WATER` or
 * `LAVA`), `traits` and `property-start`, the lowest value of each integer property that does not start at `0`. Numbers are Java literals with their vanilla precision, so the generated data widens a
 * float exactly as vanilla does: `"0.98F"` for a float, `"0.25D"` for a double; friction and the speed and jump
 * factors are floats in vanilla. Several entries may name one block, such as a tag and a block's own constants;
 * their traits add up, and one value may be set only once per block.
 */
object BlockFactsFile {
	/**
	 * The trait names the Java `BlockTrait` enum declares.
	 */
	val traits = setOf("AIR", "CLIMBABLE", "SLIME_STEP", "HONEY_SLIDE", "BUBBLE_COLUMN", "DYNAMIC_COLLISION", "OFFSET_COLLISION")

	private val fluids = setOf("WATER", "LAVA")
	private val fields = setOf("names", "source", "friction", "speed-factor", "jump-factor", "bounce", "stuck", "fluid", "traits", "property-start")
	private val literal = Regex("""\d+(\.\d+)?[FD]""")
	private val name = Regex("""minecraft:[a-z0-9_]+""")

	/**
	 * Reads and validates [file].
	 *
	 * @throws GradleException when the file cannot be parsed or violates the schema
	 */
	fun read(file: File): BlockFacts {
		val document = try {
			TomlMapper().readTree(file)
		} catch (exception: JacksonException) {
			throw GradleException("${file.name} is not valid TOML: ${exception.originalMessage}", exception)
		}
		return Entries(file.name).read(document)
	}

	private class Entries(private val source: String) {
		private val facts = linkedMapOf<String, BlockFacts.Fact>()

		fun read(document: JsonNode): BlockFacts {
			val unknown = document.fieldNames().asSequence().filter { it != "block" }.toList()
			if (unknown.isNotEmpty()) fail("has unknown tables $unknown; expected [[block]]")
			val entries = document.path("block")
			if (!entries.isArray || entries.isEmpty) fail("must declare at least one [[block]]")
			entries.forEachIndexed { index, entry -> entry(entry, "block #${index + 1}") }

			return BlockFacts(facts.toMap())
		}

		private fun entry(entry: JsonNode, position: String) {
			if (!entry.isObject) fail("$position must be a table")
			val unknown = entry.fieldNames().asSequence().filterNot(fields::contains).toList()
			if (unknown.isNotEmpty()) fail("$position has unknown fields $unknown; expected $fields")
			if (!entry.path("source").isTextual || entry.path("source").asText().isBlank())
				fail("$position must name the vanilla source of its values")

			val names = strings(entry, "names", position)
			if (names.isEmpty()) fail("$position must list at least one block name")
			names.filterNot(name::matches).forEach { fail("$position names '$it', which is not a minecraft: block name") }
			val stuck = entry.path("stuck")
			if (!stuck.isMissingNode && (!stuck.isObject || stuck.size() != 2 || !stuck.has("horizontal") || !stuck.has("vertical")))
				fail("$position stuck must be a { horizontal, vertical } table")
			val fluid = entry.path("fluid").takeUnless(JsonNode::isMissingNode)?.asText()
			if (fluid != null && fluid !in fluids) fail("$position fluid '$fluid' must be one of $fluids")
			val traits = strings(entry, "traits", position).toSet()
			traits.filterNot(BlockFactsFile.traits::contains).forEach { fail("$position names trait $it; expected one of ${BlockFactsFile.traits}") }

			val fact = BlockFacts.Fact(
				friction = float(entry, "friction", position),
				speedFactor = float(entry, "speed-factor", position),
				jumpFactor = float(entry, "jump-factor", position),
				bounce = double(entry.path("bounce"), "bounce", position),
				stuckHorizontal = double(stuck.path("horizontal"), "stuck horizontal", position),
				stuckVertical = double(stuck.path("vertical"), "stuck vertical", position),
				fluid = fluid,
				traits = traits,
				propertyStarts = propertyStarts(entry.path("property-start"), position),
			)
			names.forEach { merge(it, fact) }
		}

		private fun merge(name: String, fact: BlockFacts.Fact) {
			val current = facts[name] ?: BlockFacts.Fact()
			fun <T> pick(field: String, old: T?, new: T?): T? {
				if (old != null && new != null) fail("sets $field of $name more than once")
				return old ?: new
			}
			facts[name] = BlockFacts.Fact(
				friction = pick("friction", current.friction, fact.friction),
				speedFactor = pick("speed-factor", current.speedFactor, fact.speedFactor),
				jumpFactor = pick("jump-factor", current.jumpFactor, fact.jumpFactor),
				bounce = pick("bounce", current.bounce, fact.bounce),
				stuckHorizontal = pick("stuck", current.stuckHorizontal, fact.stuckHorizontal),
				stuckVertical = pick("stuck", current.stuckVertical, fact.stuckVertical),
				fluid = pick("fluid", current.fluid, fact.fluid),
				traits = current.traits + fact.traits,
				propertyStarts = current.propertyStarts + fact.propertyStarts.onEach { (property, _) ->
					if (property in current.propertyStarts) fail("sets the start of $name's $property more than once")
				},
			)
		}

		private fun propertyStarts(table: JsonNode, position: String): Map<String, Int> {
			if (table.isMissingNode) return emptyMap()
			if (!table.isObject || table.properties().any { !it.value.isIntegralNumber || it.value.asInt() < 1 })
				fail("$position property-start must map property names to their lowest value, a positive integer")
			return table.properties().associate { it.key to it.value.asInt() }
		}

		private fun float(entry: JsonNode, field: String, position: String): Float? {
			val value = entry.path(field)
			if (value.isMissingNode) return null
			val text = value.asText()
			if (!value.isTextual || !literal.matches(text) || !text.endsWith("F"))
				fail("$position $field must be a float literal such as \"0.98F\", as vanilla declares it")
			return text.removeSuffix("F").toFloat()
		}

		private fun double(value: JsonNode, field: String, position: String): Double? {
			if (value.isMissingNode) return null
			val text = value.asText()
			if (!value.isTextual || !literal.matches(text))
				fail("$position $field must be a Java literal with its vanilla precision, such as \"0.66F\" or \"0.25D\"")
			if (text.endsWith("F")) return text.removeSuffix("F").toFloat().toDouble()
			return text.removeSuffix("D").toDouble()
		}

		private fun strings(entry: JsonNode, field: String, position: String): List<String> {
			val value = entry.path(field)
			if (value.isMissingNode) return emptyList()
			if (!value.isArray || value.any { !it.isTextual }) fail("$position $field must be an array of strings")
			return value.map(JsonNode::asText)
		}

		private fun fail(message: String): Nothing = throw GradleException("$source: $message")
	}
}
