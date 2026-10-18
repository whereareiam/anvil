package me.whereareiam.anvil.buildlogic.fixture

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LiveMatrixReleasesTest {
	@Test
	fun `reads the releases in the file's order`() {
		val text = """
			[[release]]
			minecraft = "1.16.5"
			paper = "794"

			[[release]]
			minecraft = "26.3"
			paper = "202"

			[[neoforge]]
			minecraft = "1.21.1"
			release = "21.1.256"
		""".trimIndent()

		assertEquals(listOf("1.16.5", "26.3"), LiveMatrixReleases.parse(text))
	}

	@Test
	fun `refuses a matrix without releases, a release without a version, and a release listed twice`() {
		val empty = assertThrows<GradleException> { LiveMatrixReleases.parse("[proxy]\nvelocity-build = \"615\"") }
		assertTrue(empty.message!!.contains("no [[release]]"), empty.message)
		assertThrows<GradleException> { LiveMatrixReleases.parse("[[release]]\npaper = \"794\"") }
		assertThrows<GradleException> {
			LiveMatrixReleases.parse("[[release]]\nminecraft = \"26.3\"\n[[release]]\nminecraft = \"26.3\"")
		}
	}
}
