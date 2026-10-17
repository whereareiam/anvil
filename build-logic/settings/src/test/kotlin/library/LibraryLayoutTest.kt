package me.whereareiam.anvil.buildlogic.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class LibraryLayoutTest {
	private val root = File("/build")
	private val libraries = mapOf("mcprotocol" to File(root, "anvil-player/player-library/library-mcprotocol/mcprotocol-releases.toml"))
	private val layout = LibraryLayout.of(listOf(
		project(":anvil-player", "anvil-player", "player-api", "player-common", "player-library"),
		project(":anvil-player:player-library:library-mcprotocol", "anvil-player/player-library/library-mcprotocol", "mcprotocol-java", "mcprotocol-common"),
		project(":anvil-player:player-library:library-mcprotocol:mcprotocol-java", "anvil-player/player-library/library-mcprotocol/mcprotocol-java", "V1_16_5", "V1_21_11"),
		project(":anvil-player:player-library:library-mcprotocol:mcprotocol-common", "anvil-player/player-library/library-mcprotocol/mcprotocol-common"),
		project(":movement:movement-mcprotocol", "movement/movement-mcprotocol", "V1_16_5"),
	), libraries)

	@Test
	fun `requires module-library on family roots and module-adapter on folders holding segments`() {
		assertEquals(mapOf(
			":anvil-player:player-library:library-mcprotocol" to "module-library",
			":anvil-player:player-library:library-mcprotocol:mcprotocol-java" to "module-adapter",
			":movement:movement-mcprotocol" to "module-adapter",
		), layout.conventions)
	}

	@Test
	fun `explains a missing convention and accepts an applied one`() {
		val side = ":movement:movement-mcprotocol"

		assertNull(layout.violation(side) { it == "module-adapter" })
		assertNull(layout.violation(":anvil-player:player-library:library-mcprotocol:mcprotocol-common") { false })
		assertEquals("$side holds segments, which reach a worker only through their library side folder; it must apply id(\"module-adapter\")",
			layout.violation(side) { false })
		assertEquals(":anvil-player:player-library:library-mcprotocol owns the release data of a protocol library and must apply "
			+ "id(\"module-library\"), which pins it", layout.violation(":anvil-player:player-library:library-mcprotocol") { false })
	}

	private fun project(path: String, directory: String, vararg children: String) =
		LibraryLayout.Project(path, File(root, directory), children.toList())
}
