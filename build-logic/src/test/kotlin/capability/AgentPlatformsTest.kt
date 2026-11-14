package me.whereareiam.anvil.buildlogic.capability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentPlatformsTest {
	@TempDir
	lateinit var root: File

	@Test
	fun `names the platform families that hold an agent`() {
		File(root, "anvil-process/process-platform/platform-bukkit/bukkit-agent").mkdirs()
		File(root, "anvil-process/process-platform/platform-bukkit/paper-provider").mkdirs()
		File(root, "anvil-process/process-platform/platform-neoforge/neoforge-agent").mkdirs()
		File(root, "anvil-process/process-platform/platform-fabric/fabric-provider").mkdirs()
		File(root, "anvil-process/process-platform/notes").mkdirs()

		assertEquals(listOf("bukkit", "neoforge"), AgentPlatforms.discover(root))
	}

	@Test
	fun `names no platform for a build without platforms`() {
		assertEquals(listOf<String>(), AgentPlatforms.discover(root))
	}
}
