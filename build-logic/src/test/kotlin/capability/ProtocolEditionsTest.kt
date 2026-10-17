package me.whereareiam.anvil.buildlogic.capability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProtocolEditionsTest {
	@TempDir
	lateinit var root: File

	@Test
	fun `names each protocol folder below the player family`() {
		File(root, "anvil-player/player-protocol/protocol-java/protocol-java-api").mkdirs()
		File(root, "anvil-player/player-protocol/protocol-bedrock").mkdirs()
		File(root, "anvil-player/player-protocol/notes").mkdirs()

		assertEquals(listOf("bedrock", "java"), ProtocolEditions.discover(root))
	}

	@Test
	fun `finds no edition in a build without the player family`() {
		assertEquals(listOf<String>(), ProtocolEditions.discover(root))
	}
}
