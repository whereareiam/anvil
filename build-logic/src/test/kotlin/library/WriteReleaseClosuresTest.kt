package me.whereareiam.anvil.buildlogic.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

class WriteReleaseClosuresTest {
	@Test
	fun `writes sorted ASCII entries that a stream load reads back on any machine`() {
		val closures = mapOf(
			"1.20.6-1" to "/home/jürgen/.gradle/protocol-1.20.6-1.jar:/home/用户/.gradle/auth-2.0.jar",
			"1.18.2-1" to "C:\\Users\\Jörg\\protocol-1.18.2-1.jar",
		)

		val file = WriteReleaseClosures.closureFile(closures)

		assertTrue(file.all { it.code < 0x80 }, file)
		assertTrue(file.lines().none { it.startsWith("#") }, file)
		assertEquals(file.lines().filter { it.isNotEmpty() }.sorted(), file.lines().filter { it.isNotEmpty() })
		val read = Properties().apply { load(file.byteInputStream(Charsets.ISO_8859_1)) }
		assertEquals(closures, read.stringPropertyNames().associateWith(read::getProperty))
	}
}
