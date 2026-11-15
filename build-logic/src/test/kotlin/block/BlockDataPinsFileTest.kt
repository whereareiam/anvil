package me.whereareiam.anvil.buildlogic.block

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
	fun `reads pinned and missing rows ordered by protocol`() {
		val pins = read("""
			[source]
			repository = "PrismarineJS/minecraft-data"
			version = "3.117.0"
			commit = "${"b".repeat(40)}"

			[[release]]
			protocol = 776

			[[release]]
			protocol = 754
			blocks = { path = "data/pc/1.16.2/blocks.json", sha256 = "$checksum" }
			collision-shapes = { path = "data/pc/1.16.1/blockCollisionShapes.json", sha256 = "$checksum" }
		""".trimIndent())

		assertEquals(listOf(754, 776), pins.releases.map { it.protocol })
		assertTrue(pins.releases.first().pinned)
		assertEquals(BlockDataPins.PinnedFile("data/pc/1.16.2/blocks.json", checksum), pins.releases.first().blocks)
		assertFalse(pins.releases.last().pinned)
	}

	@Test
	fun `formats what it reads`() {
		val pins = BlockDataPins(
			BlockDataPins.Source("PrismarineJS/minecraft-data", "3.117.0", "c".repeat(40)),
			listOf(
				BlockDataPins.Release(777, null, null),
				BlockDataPins.Release(
					767,
					BlockDataPins.PinnedFile("data/pc/1.21.1/blocks.json", checksum),
					BlockDataPins.PinnedFile("data/pc/1.20.5/blockCollisionShapes.json", checksum),
				),
			),
		)

		assertEquals(pins.copy(releases = pins.releases.sortedBy { it.protocol }), read(BlockDataPinsFile.format(pins)))
	}

	@Test
	fun `refuses a row that pins only one file`() {
		val failure = assertThrows<GradleException> {
			read(source() + """
				[[release]]
				protocol = 756
				blocks = { path = "data/pc/1.17/blocks.json", sha256 = "$checksum" }
			""".trimIndent())
		}

		assertEquals("block-data.toml: protocol 756 must pin both blocks and collision-shapes, or neither", failure.message)
	}

	@Test
	fun `refuses a protocol in two rows`() {
		val failure = assertThrows<GradleException> {
			read(source() + "[[release]]\nprotocol = 766\n\n[[release]]\nprotocol = 766\n")
		}

		assertEquals("block-data.toml: lists protocol 766 more than once", failure.message)
	}

	@Test
	fun `refuses a row without a protocol number`() {
		val failure = assertThrows<GradleException> { read(source() + "[[release]]\nprotocol = \"1.20.6\"\n") }

		assertEquals("block-data.toml: release #1 must name its protocol as a positive integer", failure.message)
	}

	@Test
	fun `refuses a malformed checksum`() {
		val failure = assertThrows<GradleException> {
			read(source() + """
				[[release]]
				protocol = 756
				blocks = { path = "data/pc/1.17/blocks.json", sha256 = "ABC" }
				collision-shapes = { path = "data/pc/1.17/blockCollisionShapes.json", sha256 = "$checksum" }
			""".trimIndent())
		}

		assertEquals("block-data.toml: protocol 756 blocks sha256 must be 64 lower-case hex digits", failure.message)
	}

	private fun source(): String = "[source]\nrepository = \"PrismarineJS/minecraft-data\"\nversion = \"3.117.0\"\ncommit = \"\"\n\n"

	private fun read(text: String): BlockDataPins = BlockDataPinsFile.read(directory.resolve("block-data.toml").apply { writeText(text) })
}
