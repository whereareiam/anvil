package me.whereareiam.anvil.buildlogic.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BlockDataEncoderTest {
	private val json = ObjectMapper()
	private val source = BlockDataPins.Source("PrismarineJS/minecraft-data", "3.117.0", "d".repeat(40))
	private val release = BlockDataPins.Release(
		listOf(MinecraftVersion.parse("1.16.5"), MinecraftVersion.parse("1.16.4")),
		BlockDataPins.PinnedFile("data/pc/1.16.2/blocks.json", "e".repeat(64)),
		BlockDataPins.PinnedFile("data/pc/1.16.1/blockCollisionShapes.json", "e".repeat(64)),
	)
	private val shapes = json.readTree("""{"blocks": {"air": 0, "snow": [0, 1]}, "shapes": {"0": [], "1": [[0, 0, 0, 1, 0.125, 1]]}}""")
	private val facts = BlockFacts(mapOf(
		"minecraft:air" to BlockFacts.Fact(traits = setOf("AIR")),
		"minecraft:snow" to BlockFacts.Fact(friction = 0.98F, bounce = 0.66F.toDouble(), propertyStarts = mapOf("layers" to 1)),
	))

	@Test
	fun `encodes blocks with their facts, properties and shapes`() {
		val encoded = BlockDataEncoder.encode(source, release, blocks(snowValues = null), shapes, facts)

		assertEquals("""
			anvil-block-data 1
			source PrismarineJS/minecraft-data 3.117.0 ${"d".repeat(40)} data/pc/1.16.2/blocks.json data/pc/1.16.1/blockCollisionShapes.json
			minecraft 1.16.4,1.16.5
			states 3
			shapes 2
			-
			0.0 0.0 0.0 1.0 0.125 1.0
			blocks 2
			block minecraft:air 0 0 0 0.6 1.0 1.0 0.0 - - NONE AIR 0
			collision 0
			block minecraft:snow 1 2 1 0.98 1.0 1.0 0.6600000262260437 - - NONE - 1
			property layers 1,2
			collision 0 1

		""".trimIndent(), encoded.text)
		assertEquals(setOf("minecraft:air", "minecraft:snow"), encoded.names)
	}

	@Test
	fun `checks listed integer values against the facts`() {
		val failure = assertThrows<GradleException> {
			BlockDataEncoder.encode(source, release, blocks(snowValues = """["0", "1"]"""), shapes, facts)
		}

		assertEquals("minecraft-data data/pc/1.16.2/blocks.json for Minecraft 1.16.5 block snow starts integer property layers at 0, "
			+ "but block-facts.toml gives it the start 1; add or fix its property-start", failure.message)
	}

	@Test
	fun `refuses a block without a collision shape for each state`() {
		val failure = assertThrows<GradleException> {
			BlockDataEncoder.encode(source, release, blocks(snowValues = null), json.readTree("""{"blocks": {"air": 0, "snow": [0]}, "shapes": {"0": []}}"""), facts)
		}

		assertEquals("minecraft-data data/pc/1.16.2/blocks.json for Minecraft 1.16.5 block snow has no collision shape for each of its 2 states", failure.message)
	}

	@Test
	fun `refuses states whose properties do not combine to the block's state count`() {
		val blocks = json.readTree(blocks(snowValues = null).toString().replace("\"maxStateId\":2", "\"maxStateId\":3"))
		val failure = assertThrows<GradleException> { BlockDataEncoder.encode(source, release, blocks, shapes, facts) }

		assertEquals("minecraft-data data/pc/1.16.2/blocks.json for Minecraft 1.16.5 gives snow 3 states, but its properties combine to 2", failure.message)
	}

	private fun blocks(snowValues: String?): JsonNode = json.readTree("""
		[
			{"id": 0, "name": "air", "minStateId": 0, "maxStateId": 0, "defaultState": 0, "states": []},
			{"id": 1, "name": "snow", "minStateId": 1, "maxStateId": 2, "defaultState": 1, "states": [
				{"name": "layers", "type": "int", "num_values": 2${snowValues?.let { ", \"values\": $it" } ?: ""}}
			]}
		]
	""")
}
