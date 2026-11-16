package me.whereareiam.anvil.buildlogic.matrix

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WriteVerifiedVersionDataTest {
	private val matrix = LiveMatrix.parse("[[paper]]\nminecraft = \"1.16.5\"\njava = 11\n\n[bungeecord]\nbuild = \"2085\"\n")

	@Test
	fun `writes the verified table of what the matrix runs on the platform`() {
		assertEquals("""

			# Written by the build from live-matrix.toml: what Anvil's live suites run on this platform.
			[verified]
			"1.16.5" = [11]

		""".trimIndent(), WriteVerifiedVersionData.verifiedTable("paper", matrix))
	}

	@Test
	fun `writes nothing for a platform the matrix verifies no version of`() {
		assertEquals("", WriteVerifiedVersionData.verifiedTable("bungeecord", matrix))
		assertEquals("", WriteVerifiedVersionData.verifiedTable("spigot", matrix))
	}
}
