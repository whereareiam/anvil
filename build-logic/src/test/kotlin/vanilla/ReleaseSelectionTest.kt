package me.whereareiam.anvil.buildlogic.vanilla

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReleaseSelectionTest {
	private val matrix = linkedMapOf("1.16.5" to 11, "1.21.11" to 21, "26.3" to 25)

	@Test
	fun `selects every Paper row without a subset`() {
		assertEquals(matrix, ReleaseSelection.select(matrix, null))
	}

	@Test
	fun `keeps the matrix order of a named subset`() {
		val selected = ReleaseSelection.select(matrix, "26.3, 1.16.5")

		assertEquals(listOf("1.16.5", "26.3"), selected.keys.toList())
		assertEquals(11, selected["1.16.5"])
	}

	@Test
	fun `refuses a version the matrix runs no Paper server of`() {
		val failure = assertThrows<GradleException> { ReleaseSelection.select(matrix, "1.21.11,1.12.2") }

		assertEquals(true, failure.message!!.contains("[1.12.2]"))
	}

	@Test
	fun `refuses an empty subset`() {
		assertThrows<GradleException> { ReleaseSelection.select(matrix, " , ") }
	}
}
