package me.whereareiam.anvil.buildlogic.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PlatformLayoutTest {
	@TempDir
	lateinit var root: File

	@Test
	fun `finds version data declaring an agent in main resources only`() {
		write("provider/src/main/resources/demo/demo-versions.toml", "known = []\n\n[agent] # the installed agent\nminimumJava = 11\n")
		write("provider/src/main/resources/demo/other-versions.toml", "known = []\n")
		write("provider/src/main/resources/demo/dotted-versions.toml", "known = []\nagent.minimumJava = 11\n")
		write("provider/src/main/resources/demo/inline-versions.toml", "agent = { minimumJava = 11 }\n")
		write("provider/src/main/resources/demo/agentless-versions.toml", "agents = []\n")
		write("provider/src/test/resources/demo/test-versions.toml", "[agent]\nminimumJava = 11\n")
		write("planning/src/main/resources/demo/notes.toml", "[agent]\nminimumJava = 11\n")
		write("api/src/main/java/Api.java", "")

		val layout = PlatformLayout.of(mapOf(
			":provider" to File(root, "provider"),
			":planning" to File(root, "planning"),
			":api" to File(root, "api"),
		))

		assertEquals(
			mapOf(":provider" to listOf("demo/demo-versions.toml", "demo/dotted-versions.toml", "demo/inline-versions.toml")),
			layout.agentData,
		)
	}

	@Test
	fun `explains a missing convention and accepts an applied one`() {
		val layout = PlatformLayout(mapOf(":provider" to listOf("demo/demo-versions.toml")))

		assertNull(layout.violation(":provider") { it == "module-platform-provider" })
		assertNull(layout.violation(":api") { false })
		assertEquals(":provider ships platform version data [demo/demo-versions.toml] but does not apply "
			+ "id(\"module-platform-provider\"), which checks the platform agent against it", layout.violation(":provider") { false })
	}

	private fun write(path: String, text: String) {
		File(root, path).apply {
			parentFile.mkdirs()
			writeText(text)
		}
	}
}
