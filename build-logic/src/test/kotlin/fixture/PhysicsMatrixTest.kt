package me.whereareiam.anvil.buildlogic.fixture

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PhysicsMatrixTest {
	private val pins = """
		[grim]
		url = "https://example.org/grim.jar"
		sha256 = "${"a".repeat(64)}"

		[sonar]
		url = "https://example.org/sonar.jar"
		sha256 = "${"b".repeat(64)}"
	""".trimIndent()

	@Test
	fun `reads the releases in the file's order and the pins`() {
		val matrix = PhysicsMatrix.parse(pins + """

			[[release]]
			minecraft = "1.16.5"
			grim = true

			[[release]]
			minecraft = "26.1.2"
			sonar = true
		""".trimIndent())

		assertEquals(listOf("1.16.5", "26.1.2"), matrix.releases)
		assertEquals(PhysicsMatrix.Pin("https://example.org/grim.jar", "a".repeat(64)), matrix.grim)
		assertEquals(PhysicsMatrix.Pin("https://example.org/sonar.jar", "b".repeat(64)), matrix.sonar)
	}

	@Test
	fun `refuses a matrix without releases, a release listed twice, and a pin without a checksum`() {
		val empty = assertThrows<GradleException> { PhysicsMatrix.parse(pins) }
		assertTrue(empty.message!!.contains("no [[release]]"), empty.message)
		assertThrows<GradleException> {
			PhysicsMatrix.parse(pins + "\n[[release]]\nminecraft = \"26.1.2\"\n[[release]]\nminecraft = \"26.1.2\"")
		}
		val unpinned = assertThrows<GradleException> {
			PhysicsMatrix.parse(pins.replace("a".repeat(64), "") + "\n[[release]]\nminecraft = \"1.16.5\"")
		}
		assertTrue(unpinned.message!!.contains("[grim]"), unpinned.message)
	}
}
