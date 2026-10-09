package me.whereareiam.anvil.buildlogic.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class LibraryLayoutTest {
	private val root = File("/build")
	private val libraries = mapOf("mcprotocol" to File(root, "anvil-protocol/protocol-mcprotocol/mcprotocol-releases.toml"))
	private val layout = LibraryLayout.of(listOf(
		project(":anvil-protocol", "anvil-protocol", "protocol-api", "protocol-mcprotocol"),
		project(":anvil-protocol:protocol-mcprotocol", "anvil-protocol/protocol-mcprotocol", "mcprotocol-client", "mcprotocol-common"),
		project(":anvil-protocol:protocol-mcprotocol:mcprotocol-client", "anvil-protocol/protocol-mcprotocol/mcprotocol-client", "V1_16_5", "V1_21_11"),
		project(":anvil-protocol:protocol-mcprotocol:mcprotocol-common", "anvil-protocol/protocol-mcprotocol/mcprotocol-common"),
		project(":movement:movement-mcprotocol", "movement/movement-mcprotocol", "V1_16_5"),
	), libraries)

	@Test
	fun `requires library-releases on family roots and segmented on folders holding segments`() {
		assertEquals(mapOf(
			":anvil-protocol:protocol-mcprotocol" to "library-releases",
			":anvil-protocol:protocol-mcprotocol:mcprotocol-client" to "segmented",
			":movement:movement-mcprotocol" to "segmented",
		), layout.conventions)
	}

	@Test
	fun `explains a missing convention and accepts an applied one`() {
		val side = ":movement:movement-mcprotocol"

		assertNull(layout.violation(side) { it == "segmented" })
		assertNull(layout.violation(":anvil-protocol:protocol-mcprotocol:mcprotocol-common") { false })
		assertEquals("$side holds segments, which reach a worker only through their library side folder; it must apply id(\"segmented\")",
			layout.violation(side) { false })
		assertEquals(":anvil-protocol:protocol-mcprotocol owns the release data of a protocol library and must apply "
			+ "id(\"library-releases\"), which pins it", layout.violation(":anvil-protocol:protocol-mcprotocol") { false })
	}

	private fun project(path: String, directory: String, vararg children: String) =
		LibraryLayout.Project(path, File(root, directory), children.toList())
}
