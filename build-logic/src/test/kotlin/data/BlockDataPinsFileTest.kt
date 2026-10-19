package me.whereareiam.anvil.buildlogic.data

import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BlockDataPinsFileTest {
	@TempDir
	lateinit var directory: File

	private val checksum = "a".repeat(64)

	@Test
	fun `reads pinned and missing rows ordered by key`() {
		val pins = read("""
			[source]
			repository = "PrismarineJS/minecraft-data"
			version = "3.117.0"
			commit = "${"b".repeat(40)}"

			[[release]]
			minecraft = ["26.2"]

			[[release]]
			minecraft = ["1.16.4", "1.16.5"]
			blocks = { path = "data/pc/1.16.2/blocks.json", sha256 = "$checksum" }
			collision-shapes = { path = "data/pc/1.16.1/blockCollisionShapes.json", sha256 = "$checksum" }
		""".trimIndent())

		assertEquals(listOf(MinecraftVersion.parse("1.16.5"), MinecraftVersion.parse("26.2")), pins.releases.map { it.key })
		assertTrue(pins.releases.first().pinned)
		assertEquals(BlockDataPins.PinnedFile("data/pc/1.16.2/blocks.json", checksum), pins.releases.first().blocks)
		assertFalse(pins.releases.last().pinned)
	}

	@Test
	fun `formats what it reads`() {
		val pins = BlockDataPins(
			BlockDataPins.Source("PrismarineJS/minecraft-data", "3.117.0", "c".repeat(40)),
			listOf(
				BlockDataPins.Release(listOf(MinecraftVersion.parse("26.3")), null, null),
				BlockDataPins.Release(
					listOf(MinecraftVersion.parse("1.21"), MinecraftVersion.parse("1.21.1")),
					BlockDataPins.PinnedFile("data/pc/1.21.1/blocks.json", checksum),
					BlockDataPins.PinnedFile("data/pc/1.20.5/blockCollisionShapes.json", checksum),
				),
			),
		)

		assertEquals(pins.copy(releases = pins.releases.sortedBy { it.key }), read(BlockDataPinsFile.format(pins)))
	}

	@Test
	fun `refuses a row that pins only one file`() {
		val failure = assertThrows<GradleException> {
			read(source() + """
				[[release]]
				minecraft = ["1.17.1"]
				blocks = { path = "data/pc/1.17/blocks.json", sha256 = "$checksum" }
			""".trimIndent())
		}

		assertEquals("block-data.toml: release 1.17.1 must pin both blocks and collision-shapes, or neither", failure.message)
	}

	@Test
	fun `refuses a Minecraft version in two rows`() {
		val failure = assertThrows<GradleException> {
			read(source() + "[[release]]\nminecraft = [\"1.20.6\"]\n\n[[release]]\nminecraft = [\"1.20.5\", \"1.20.6\"]\n")
		}

		assertEquals("block-data.toml: assigns Minecraft 1.20.6 to more than one release", failure.message)
	}

	@Test
	fun `refuses a malformed checksum`() {
		val failure = assertThrows<GradleException> {
			read(source() + """
				[[release]]
				minecraft = ["1.17.1"]
				blocks = { path = "data/pc/1.17/blocks.json", sha256 = "ABC" }
				collision-shapes = { path = "data/pc/1.17/blockCollisionShapes.json", sha256 = "$checksum" }
			""".trimIndent())
		}

		assertEquals("block-data.toml: release 1.17.1 blocks sha256 must be 64 lower-case hex digits", failure.message)
	}

	private fun source(): String = "[source]\nrepository = \"PrismarineJS/minecraft-data\"\nversion = \"3.117.0\"\ncommit = \"\"\n\n"

	private fun read(text: String): BlockDataPins = BlockDataPinsFile.read(directory.resolve("block-data.toml").apply { writeText(text) })
}
