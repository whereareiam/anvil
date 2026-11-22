package me.whereareiam.anvil.buildlogic.vanilla.client

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VanillaClientPinsFileTest {
	private val sha1 = "a".repeat(40)
	private val manifest = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

	@Test
	fun `reads pinned, unobfuscated and unpinned rows in order`() {
		val pins = VanillaClientPinsFile.parse("""
			[source]
			manifest = "$manifest"

			[[release]]
			minecraft = "1.21.11"
			harness = "v1_21_11"
			metadata = { url = "https://piston-meta.mojang.com/v1/packages/$sha1/1.21.11.json", sha1 = "$sha1" }
			client = { url = "https://piston-data.mojang.com/v1/objects/$sha1/client.jar", sha1 = "$sha1" }
			mappings = { url = "https://piston-data.mojang.com/v1/objects/$sha1/client.txt", sha1 = "$sha1" }

			[[release]]
			minecraft = "26.3"
			harness = "v26"
			metadata = { url = "https://piston-meta.mojang.com/v1/packages/$sha1/26.3.json", sha1 = "$sha1" }
			client = { url = "https://piston-data.mojang.com/v1/objects/$sha1/client.jar", sha1 = "$sha1" }

			[[release]]
			minecraft = "27.1"
			harness = "v26"
		""".trimIndent())

		assertEquals(manifest, pins.manifest)
		assertEquals(listOf("1.21.11", "26.3", "27.1"), pins.releases.map { it.minecraft })
		assertTrue(pins.release("1.21.11")!!.pinned)
		assertEquals(sha1, pins.release("1.21.11")!!.mappings!!.sha1)
		assertTrue(pins.release("26.3")!!.pinned)
		assertNull(pins.release("26.3")!!.mappings)
		assertFalse(pins.release("27.1")!!.pinned)
		assertNull(pins.release("1.12.2"))
	}

	@Test
	fun `formats what it reads`() {
		val pins = VanillaClientPins(manifest, listOf(
			VanillaClientPins.Release("1.16.5", "v1_16", file("1.16.5.json"), file("client.jar"), file("client.txt")),
			VanillaClientPins.Release("26.3", "v26", file("26.3.json"), file("client.jar"), null),
			VanillaClientPins.Release("27.1", "v26", null, null, null),
		))

		assertEquals(pins, VanillaClientPinsFile.parse(VanillaClientPinsFile.format(pins)))
	}

	@Test
	fun `refuses a metadata pin without its client`() {
		val failure = assertThrows<GradleException> {
			VanillaClientPinsFile.parse(document("""metadata = { url = "https://example.com/m.json", sha1 = "$sha1" }"""))
		}

		assertTrue(failure.message!!.contains("metadata and client together"))
	}

	@Test
	fun `refuses a checksum that is no SHA-1`() {
		assertThrows<GradleException> {
			VanillaClientPinsFile.parse(document("""
				metadata = { url = "https://example.com/m.json", sha1 = "$sha1" }
				client = { url = "https://example.com/c.jar", sha1 = "${"b".repeat(64)}" }
			""".trimIndent()))
		}
	}

	@Test
	fun `refuses a harness that names no family source set`() {
		assertThrows<GradleException> { VanillaClientPinsFile.parse(document("", harness = "latest")) }
	}

	@Test
	fun `refuses unknown fields and repeated versions`() {
		assertThrows<GradleException> { VanillaClientPinsFile.parse(document("server = true")) }
		assertThrows<GradleException> {
			VanillaClientPinsFile.parse(document("") + "\n[[release]]\nminecraft = \"1.21.11\"\nharness = \"v1_21_11\"\n")
		}
	}

	private fun file(name: String) = VanillaClientPins.PinnedFile("https://piston-data.mojang.com/v1/objects/$sha1/$name", sha1)

	private fun document(fields: String, harness: String = "v1_21_11"): String = """
		[source]
		manifest = "$manifest"

		[[release]]
		minecraft = "1.21.11"
		harness = "$harness"
	""".trimIndent() + "\n" + fields + "\n"
}
