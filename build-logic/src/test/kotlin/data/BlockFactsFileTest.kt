package me.whereareiam.anvil.buildlogic.data

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BlockFactsFileTest {
	@TempDir
	lateinit var directory: File

	@Test
	fun `merges the entries of one block and widens floats as vanilla does`() {
		val facts = read("""
			[[block]]
			names = ["minecraft:powder_snow"]
			source = "PowderSnowBlock.entityInside"
			stuck = { horizontal = "0.9F", vertical = "1.5D" }

			[[block]]
			names = ["minecraft:powder_snow", "minecraft:scaffolding"]
			source = "dynamic shapes"
			traits = ["DYNAMIC_COLLISION"]

			[[block]]
			names = ["minecraft:snow"]
			source = "SnowLayerBlock.LAYERS"
			friction = "0.98F"
			property-start = { layers = 1 }
		""".trimIndent())

		val snow = facts.of("minecraft:powder_snow")
		assertEquals(0.9F.toDouble(), snow.stuckHorizontal)
		assertEquals(1.5, snow.stuckVertical)
		assertEquals(setOf("DYNAMIC_COLLISION"), snow.traits)
		assertEquals(setOf("DYNAMIC_COLLISION"), facts.of("minecraft:scaffolding").traits)
		assertEquals(0.98F, facts.of("minecraft:snow").friction)
		assertEquals(mapOf("layers" to 1), facts.of("minecraft:snow").propertyStarts)
		assertEquals(BlockFacts.Fact(), facts.of("minecraft:stone"))
	}

	@Test
	fun `refuses a value set twice for one block`() {
		val failure = assertThrows<GradleException> {
			read("""
				[[block]]
				names = ["minecraft:ice"]
				source = "a"
				friction = "0.98F"

				[[block]]
				names = ["minecraft:ice"]
				source = "b"
				friction = "0.5F"
			""".trimIndent())
		}

		assertEquals("block-facts.toml: sets friction of minecraft:ice more than once", failure.message)
	}

	@Test
	fun `refuses a friction without its vanilla float precision`() {
		val failure = assertThrows<GradleException> {
			read("[[block]]\nnames = [\"minecraft:ice\"]\nsource = \"a\"\nfriction = \"0.98D\"\n")
		}

		assertEquals("block-facts.toml: block #1 friction must be a float literal such as \"0.98F\", as vanilla declares it", failure.message)
	}

	@Test
	fun `refuses an entry without its vanilla source`() {
		val failure = assertThrows<GradleException> { read("[[block]]\nnames = [\"minecraft:ice\"]\nfriction = \"0.98F\"\n") }

		assertEquals("block-facts.toml: block #1 must name the vanilla source of its values", failure.message)
	}

	@Test
	fun `refuses an unknown trait`() {
		val failure = assertThrows<GradleException> { read("[[block]]\nnames = [\"minecraft:ice\"]\nsource = \"a\"\ntraits = [\"SLIPPERY\"]\n") }

		assertEquals("block-facts.toml: block #1 names trait SLIPPERY; expected one of ${BlockFactsFile.traits}", failure.message)
	}

	private fun read(text: String): BlockFacts = BlockFactsFile.read(directory.resolve("block-facts.toml").apply { writeText(text) })
}
