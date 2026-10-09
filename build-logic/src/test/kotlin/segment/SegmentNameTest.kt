package me.whereareiam.anvil.buildlogic.segment

import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SegmentNameTest {
	@Test
	fun `reads the start version and package of a patch release`() {
		val segment = SegmentName.parse("V1_16_5")

		assertEquals(MinecraftVersion.parse("1.16.5"), segment.since)
		assertEquals("V1_16_5", segment.projectName)
		assertEquals("v1_16_5", segment.packageName)
	}

	@Test
	fun `reads major and minor only versions`() {
		val segment = SegmentName.parse("V26_1")

		assertEquals(MinecraftVersion.parse("26.1"), segment.since)
		assertEquals("v26_1", segment.packageName)
	}

	@ParameterizedTest
	@ValueSource(strings = ["v1_16_5", "V1", "V1_16_5_1", "V1.16.5", "1_16_5", "messages-mcprotocol-1_16_5", "V1_16_", "V1__16"])
	fun `rejects names that are not segment names`(name: String) {
		val failure = assertThrows<GradleException> { SegmentName.parse(name) }

		assertTrue(failure.message!!.contains("V<major>_<minor>[_<patch>]"), failure.message)
	}

	@ParameterizedTest
	@ValueSource(strings = ["V1_20_0", "V01_16_5", "V1_016"])
	fun `rejects names that are not written canonically`(name: String) {
		val failure = assertThrows<GradleException> { SegmentName.parse(name) }

		assertTrue(failure.message!!.contains("must be named V1_"), failure.message)
	}
}
