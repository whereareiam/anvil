package me.whereareiam.anvil.buildlogic.data

import com.fasterxml.jackson.databind.JsonNode
import org.gradle.api.GradleException

/**
 * Turns one release's minecraft-data `blocks.json` and `blockCollisionShapes.json`, together with the block facts,
 * into the compact text that `protocol-java-data` reads at runtime.
 *
 * The data is validated as it is encoded: block ids are the blocks' positions, their state ids run without gaps from
 * `0`, each block's state count is the product of its properties' value counts, its default state is one of its
 * states, and every block has a collision shape for each state. Values of boolean properties are `true, false`,
 * vanilla's order, which minecraft-data leaves out; values of integer properties it leaves out run from the
 * property's start in the facts, or from `0`, and listed ones must agree with the facts.
 *
 * The format, line by line: `anvil-block-data 1`; `source <repository> <version> <commit> <blocks> <shapes>`;
 * `minecraft <versions>`; `states <count>`; `shapes <count>` followed by one line per shape, `-` for an empty one or
 * its boxes as six block-local coordinates `minX minY minZ maxX maxY maxZ` joined by ` / `; `blocks <count>` followed
 * by each block: `block <name> <minState> <maxState> <defaultState> <friction> <speedFactor> <jumpFactor> <bounce>
 * <stuckHorizontal> <stuckVertical> <fluid> <traits> <properties>`, its `property <name> <values>` lines in
 * minecraft-data's order, whose last property varies fastest between state ids, and `collision <shape>`, one shape
 * for every state or one per state. Absent values are `-`, lists are joined by `,`.
 */
object BlockDataEncoder {
	/**
	 * The format version the first line carries.
	 */
	const val FORMAT = 1

	private const val defaultFriction = 0.6F
	private const val defaultFactor = 1.0F

	/**
	 * The encoded data and the namespaced names of its blocks.
	 */
	data class Encoded(val text: String, val names: Set<String>)

	/**
	 * Encodes the block data of [release].
	 *
	 * @param source the pinned minecraft-data release
	 * @param blocks the release's `blocks.json`
	 * @param shapes the release's `blockCollisionShapes.json`
	 * @throws GradleException when the data is inconsistent
	 */
	fun encode(source: BlockDataPins.Source, release: BlockDataPins.Release, blocks: JsonNode, shapes: JsonNode, facts: BlockFacts): Encoded {
		val context = "minecraft-data ${release.blocks!!.path} for Minecraft ${release.key}"
		if (!blocks.isArray || blocks.isEmpty) fail(context, "must be a non-empty array of blocks")
		val table = shapeTable(shapes.path("shapes"), context)
		val output = StringBuilder()
		output.append("anvil-block-data $FORMAT\n")
		output.append("source ${source.repository} ${source.version} ${source.commit} ${release.blocks.path} ${release.collisionShapes!!.path}\n")
		output.append("minecraft ${release.minecraft.sorted().joinToString(",")}\n")
		output.append("states ${blocks.last().path("maxStateId").asInt() + 1}\n")
		output.append("shapes ${table.size}\n")
		table.forEach { output.append(it).append('\n') }
		output.append("blocks ${blocks.size()}\n")

		val names = linkedSetOf<String>()
		var nextState = 0
		blocks.forEachIndexed { index, block ->
			val name = block.path("name").asText()
			val namespaced = "minecraft:$name"
			if (block.path("id").asInt(-1) != index) fail(context, "lists $name at position $index with id ${block.path("id")}")
			if (!names.add(namespaced)) fail(context, "lists $name more than once")

			val minState = block.path("minStateId").asInt(-1)
			val maxState = block.path("maxStateId").asInt(-1)
			val defaultState = block.path("defaultState").asInt(-1)
			if (minState != nextState || maxState < minState) fail(context, "gives $name the states $minState..$maxState after state ${nextState - 1}")
			if (defaultState !in minState..maxState) fail(context, "gives $name the default state $defaultState outside $minState..$maxState")
			nextState = maxState + 1

			val fact = facts.of(namespaced)
			val properties = properties(block.path("states"), fact, "$context block $name")
			val stateCount = maxState - minState + 1
			val combinations = properties.fold(1L) { product, property -> product * property.second.size }
			if (combinations != stateCount.toLong()) fail(context, "gives $name $stateCount states, but its properties combine to $combinations")

			output.append("block $namespaced $minState $maxState $defaultState ")
			output.append("${fact.friction ?: defaultFriction} ${fact.speedFactor ?: defaultFactor} ${fact.jumpFactor ?: defaultFactor} ")
			output.append("${fact.bounce ?: 0.0} ${fact.stuckHorizontal ?: "-"} ${fact.stuckVertical ?: "-"} ")
			output.append("${fact.fluid ?: "NONE"} ${fact.traits.sorted().joinToString(",").ifEmpty { "-" }} ${properties.size}\n")
			properties.forEach { (property, values) -> output.append("property $property ${values.joinToString(",")}\n") }
			output.append("collision ${collision(shapes.path("blocks").path(name), stateCount, table.size, "$context block $name")}\n")
		}

		return Encoded(output.toString(), names)
	}

	private fun shapeTable(shapes: JsonNode, context: String): List<String> {
		if (!shapes.isObject || shapes.isEmpty) fail(context, "has no collision shape table")
		return (0 until shapes.size()).map { index ->
			val boxes = shapes.path(index.toString())
			if (!boxes.isArray) fail(context, "numbers its collision shapes with gaps; shape $index is missing")
			if (boxes.isEmpty) return@map "-"
			boxes.joinToString(" / ") { box ->
				if (!box.isArray || box.size() != 6 || box.any { !it.isNumber }) fail(context, "has a box of shape $index that is not six numbers")
				box.joinToString(" ") { it.asDouble().toString() }
			}
		}
	}

	private fun properties(states: JsonNode, fact: BlockFacts.Fact, context: String): List<Pair<String, List<String>>> {
		if (!states.isArray) fail(context, "has no state properties")
		val properties = states.map { state ->
			val name = state.path("name").asText()
			val count = state.path("num_values").asInt(0)
			if (name.isBlank() || count < 1) fail(context, "has a property without a name or values")
			name to when (state.path("type").asText()) {
				"bool" -> listOf("true", "false")
				"int" -> integerValues(name, count, state.path("values"), fact.propertyStarts[name], context)
				"enum" -> state.path("values").map(JsonNode::asText)
				else -> fail(context, "has property $name of unknown type ${state.path("type")}")
			}.also { if (it.size != count) fail(context, "gives property $name $count values, but lists ${it.size}") }
		}
		fact.propertyStarts.keys.filterNot { property -> properties.any { it.first == property } }
			.forEach { fail(context, "has no integer property $it, which block-facts.toml gives a start") }

		return properties
	}

	private fun integerValues(name: String, count: Int, listed: JsonNode, start: Int?, context: String): List<String> {
		if (listed.isMissingNode) return (0 until count).map { ((start ?: 0) + it).toString() }

		val values = listed.map(JsonNode::asText)
		val first = values.firstOrNull()?.toIntOrNull() ?: fail(context, "lists integer property $name without integer values")
		if (values != (0 until count).map { (first + it).toString() }) fail(context, "lists integer property $name with values that are not consecutive")
		if (first != (start ?: 0))
			fail(context, "starts integer property $name at $first, but block-facts.toml gives it the start ${start ?: 0}; add or fix its property-start")

		return values
	}

	private fun collision(shape: JsonNode, stateCount: Int, shapeCount: Int, context: String): String {
		val indices = when {
			shape.isInt -> listOf(shape.asInt())
			shape.isArray && shape.size() == stateCount && shape.all(JsonNode::isInt) -> shape.map(JsonNode::asInt)
			else -> fail(context, "has no collision shape for each of its $stateCount states")
		}
		indices.filterNot { it in 0 until shapeCount }.forEach { fail(context, "names collision shape $it, which the table does not have") }

		return indices.joinToString(" ")
	}

	private fun fail(context: String, message: String): Nothing = throw GradleException("$context $message")
}
