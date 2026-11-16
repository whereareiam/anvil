package me.whereareiam.anvil.buildlogic.matrix

import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WriteVerifiedReleasesTest {
	@Test
	fun `adds each release's versions a server of the matrix runs after its minecraft line`() {
		val releases = """
			[[release]]
			version = "1.16.5-2"
			minecraft = ["1.16.4", "1.16.5"]
			java = 8

			[[release.artifact]]
			module = "demo:protocol:1.16.5-2"

			[[release]]
			version = "26.1.2-1"
			minecraft = ["26.1", "26.1.1", "26.1.2"]
		""".trimIndent().lines()
		val servers = setOf(MinecraftVersion.parse("1.16.5"))

		assertEquals("""
			[[release]]
			version = "1.16.5-2"
			minecraft = ["1.16.4", "1.16.5"]
			verified = ["1.16.5"]
			java = 8

			[[release.artifact]]
			module = "demo:protocol:1.16.5-2"

			[[release]]
			version = "26.1.2-1"
			minecraft = ["26.1", "26.1.1", "26.1.2"]
			verified = []

		""".trimIndent(), WriteVerifiedReleases.withVerified(releases, servers))
	}
}
