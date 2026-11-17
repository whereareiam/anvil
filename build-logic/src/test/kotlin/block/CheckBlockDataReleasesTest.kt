package me.whereareiam.anvil.buildlogic.block

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CheckBlockDataReleasesTest {
	@Test
	fun `accepts block data for exactly the libraries' protocols`() {
		assertEquals(emptyList<String>(), CheckBlockDataReleases.problems(
			listOf("754.blocks", "777.blocks"),
			listOf(754 to "mcprotocol-releases.toml 1.16.5", 777 to "mcprotocol-releases.toml 26.3"),
		))
	}

	@Test
	fun `reports a protocol without block data, block data of no protocol and a misnamed file`() {
		assertEquals(listOf(
			"latest.blocks is not named <protocol>.blocks",
			"mcprotocol-releases.toml 26.3 speaks protocol 777, which has no block data",
			"340.blocks holds block data of a protocol no protocol library release speaks",
		), CheckBlockDataReleases.problems(
			listOf("754.blocks", "340.blocks", "latest.blocks"),
			listOf(754 to "mcprotocol-releases.toml 1.16.5", 777 to "mcprotocol-releases.toml 26.3"),
		))
	}
}
