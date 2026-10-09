package me.whereareiam.anvil.buildlogic.segment

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibrarySideTest {
	@TempDir
	lateinit var root: File

	@Test
	fun `lists segment projects by start version and ignores other folders`() {
		val side = side("messages-mcprotocol", "V1_20_6", "V1_16_5", "V26_1")
		File(side, "src/main/java").mkdirs()
		File(side, "V1_18_2").mkdirs()

		val read = LibrarySide.read(side, listOf("mcprotocol"))

		assertEquals("mcprotocol", read.library)
		assertEquals(listOf("V1_16_5", "V1_20_6", "V26_1"), read.segments.map(SegmentName::projectName))
	}

	@Test
	fun `rejects child projects that are not segments`() {
		val side = side("messages-mcprotocol", "V1_16_5", "legacy")

		val failure = assertThrows<GradleException> { LibrarySide.read(side, listOf("mcprotocol")) }

		assertTrue(failure.message!!.contains("may contain only segments"), failure.message)
	}

	@Test
	fun `requires at least one segment`() {
		val side = side("mcprotocol-client")

		val failure = assertThrows<GradleException> { LibrarySide.read(side, listOf("mcprotocol")) }

		assertTrue(failure.message!!.contains("at least one segment"), failure.message)
	}

	private fun side(name: String, vararg projects: String): File {
		val side = File(root, name)
		side.mkdirs()
		projects.forEach { project ->
			File(side, project).mkdirs()
			File(side, "$project/build.gradle.kts").writeText("")
		}
		return side
	}
}
