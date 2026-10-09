package me.whereareiam.anvil.buildlogic.platform

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PlatformVersionsFileTest {
	@TempDir
	lateinit var directory: File

	@Test
	fun `reads the agent's minimum Java`() {
		assertEquals(11, PlatformVersionsFile.agentMinimumJava(write("[agent]\nminimumJava = 11\n\n[[java]]\nminimum = 17\npreferred = 17\n")))
	}

	@Test
	fun `reports no minimum when the data declares no agent`() {
		assertNull(PlatformVersionsFile.agentMinimumJava(write("[[java]]\nminimum = 17\npreferred = 17\n")))
	}

	@Test
	fun `refuses a declaration that is not a Java feature version`() {
		val failure = assertThrows<GradleException> { PlatformVersionsFile.agentMinimumJava(write("[agent]\nminimumJava = \"11\"\n")) }

		assertTrue(failure.message!!.contains("demo-versions.toml must declare [agent] minimumJava"), failure.message)
	}

	private fun write(text: String): File = File(directory, "demo-versions.toml").apply { writeText(text) }
}
