package me.whereareiam.anvil.buildlogic.matrix

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LiveMatrixTest {
	private val matrix = LiveMatrix.parse("""
		[[paper]]
		minecraft = "1.16.5"
		build = "794"
		java = 11

		[[paper]]
		minecraft = "26.3"
		build = "202"
		java = 25

		[[spigot]]
		minecraft = "26.3"
		sha256 = "abc"
		java = 25

		[velocity]
		version = "4.3.1"
		build = "45"
		java = 25

		[bungeecord]
		build = "2085"
	""".trimIndent())

	@Test
	fun `verifies each row's version on its Java, per platform`() {
		assertEquals(mapOf("1.16.5" to 11, "26.3" to 25), matrix.verified("paper"))
		assertEquals(mapOf("4.3.1" to 25), matrix.verified("velocity"))
		assertEquals(emptyMap<String, Int>(), matrix.verified("bungeecord"))
		assertEquals(emptyMap<String, Int>(), matrix.verified("neoforge"))
	}

	@Test
	fun `lists the Minecraft versions of servers, not the versions of proxies`() {
		assertEquals(listOf("1.16.5", "26.3"), matrix.minecraftVersions("paper"))
		assertEquals(setOf("1.16.5", "26.3"), matrix.serverVersions)
	}

	@Test
	fun `refuses a version listed twice, Java without a version, and a version that is none`() {
		assertFailure("[paper] lists 26.3 more than once", "[[paper]]\nminecraft = \"26.3\"\n[[paper]]\nminecraft = \"26.3\"")
		assertFailure("names java but no minecraft or version", "[bungeecord]\nbuild = \"2085\"\njava = 21")
		assertFailure("minecraft '\"latest\"' is not a version", "[[paper]]\nminecraft = \"latest\"")
		assertFailure("lists no platform", "")
	}

	private fun assertFailure(message: String, text: String) {
		val failure = assertThrows<GradleException> { LiveMatrix.parse(text) }

		assertTrue(failure.message!!.contains(message), failure.message)
	}
}
