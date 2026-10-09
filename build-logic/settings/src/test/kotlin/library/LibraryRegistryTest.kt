package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibraryRegistryTest {
	@TempDir
	lateinit var root: File

	@Test
	fun `registers libraries that own a releases file in their protocol folder`() {
		val mcprotocol = releases("protocol-mcprotocol", "mcprotocol")
		val other = releases("protocol-other", "other")
		File(root, "anvil-protocol/protocol-api").mkdirs()
		releases("protocol-misnamed", "different")

		assertEquals(mapOf("mcprotocol" to mcprotocol, "other" to other), LibraryRegistry.discover(root))
	}

	@Test
	fun `finds no libraries without a protocol family`() {
		assertEquals(emptyMap<String, File>(), LibraryRegistry.discover(root))
	}

	@Test
	fun `rejects library ids that are not one word`() {
		releases("protocol-two-words", "two-words")

		assertThrows<GradleException> { LibraryRegistry.discover(root) }
	}

	@Test
	fun `names the one library among a side folder's words`() {
		assertEquals("mcprotocol", LibraryRegistry.libraryOf("messages-mcprotocol", listOf("mcprotocol", "other")))
		assertEquals("mcprotocol", LibraryRegistry.libraryOf("mcprotocol-client", listOf("mcprotocol")))
	}

	@Test
	fun `rejects side folders naming no library or several`() {
		val none = assertThrows<GradleException> { LibraryRegistry.libraryOf("messages-mcprotocollib", listOf("mcprotocol")) }
		val several = assertThrows<GradleException> { LibraryRegistry.libraryOf("mcprotocol-other", listOf("mcprotocol", "other")) }

		assertTrue(none.message!!.contains("exactly one registered library"), none.message)
		assertTrue(several.message!!.contains("named: [mcprotocol, other]"), several.message)
	}

	private fun releases(folder: String, library: String): File {
		val directory = File(root, "anvil-protocol/$folder")
		directory.mkdirs()
		return File(directory, "$library-releases.toml").apply { writeText("") }
	}
}
