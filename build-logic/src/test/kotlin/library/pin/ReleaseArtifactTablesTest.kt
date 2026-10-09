package me.whereareiam.anvil.buildlogic.library.pin

import me.whereareiam.anvil.buildlogic.library.ReleaseArtifact
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReleaseArtifactTablesTest {
	private val text = """
		# Releases of the demo library.

		[[release]]
		version = "1.18.2-1"
		# Hand-written note inside the release table.
		module = "demo:protocol:1.18.2-1"
		minecraft = ["1.18.2"]

		[[release.artifact]]
		module = "demo:protocol:1.18.2-1"
		url = "https://old.example/protocol-1.18.2-1.jar"
		sha256 = ""

		[[release.artifact]]
		module = "demo:gone:1.0"
		url = "https://old.example/gone-1.0.jar"
		sha256 = ""

		# Snapshot releases follow.
		[[release]]
		version = "1.20.6-1"
		module = "demo:protocol:1.20.6-1"
		minecraft = ["1.20.5", "1.20.6"]

		[[release.artifact]]
		module = "demo:protocol:1.20.6-1"
		url = "https://old.example/protocol-1.20.6-1.jar"
		sha256 = ""
	""".trimIndent() + "\n"

	@Test
	fun `replaces only the artifact tables and keeps comments and release fields`() {
		val replaced = ReleaseArtifactTables.replace(text, mapOf(
			"1.18.2-1" to listOf(artifact("demo:protocol:1.18.2-1"), artifact("demo:native:1.0:linux-x86_64")),
			"1.20.6-1" to listOf(artifact("demo:protocol:1.20.6-1")),
		))

		assertEquals("""
			# Releases of the demo library.

			[[release]]
			version = "1.18.2-1"
			# Hand-written note inside the release table.
			module = "demo:protocol:1.18.2-1"
			minecraft = ["1.18.2"]

			[[release.artifact]]
			module = "demo:protocol:1.18.2-1"
			url = "file:/repository/demo:protocol:1.18.2-1.jar"
			sha256 = "${"a".repeat(64)}"

			[[release.artifact]]
			module = "demo:native:1.0:linux-x86_64"
			url = "file:/repository/demo:native:1.0:linux-x86_64.jar"
			sha256 = "${"a".repeat(64)}"

			# Snapshot releases follow.
			[[release]]
			version = "1.20.6-1"
			module = "demo:protocol:1.20.6-1"
			minecraft = ["1.20.5", "1.20.6"]

			[[release.artifact]]
			module = "demo:protocol:1.20.6-1"
			url = "file:/repository/demo:protocol:1.20.6-1.jar"
			sha256 = "${"a".repeat(64)}"
		""".trimIndent() + "\n", replaced)
	}

	@Test
	fun `writes the same text again when the closures do not change`() {
		val closures = mapOf(
			"1.18.2-1" to listOf(artifact("demo:protocol:1.18.2-1")),
			"1.20.6-1" to listOf(artifact("demo:protocol:1.20.6-1")),
		)
		val once = ReleaseArtifactTables.replace(text, closures)

		assertEquals(once, ReleaseArtifactTables.replace(once, closures))
	}

	@Test
	fun `refuses comments inside artifact tables, unknown tables and releases without a closure`() {
		val commented = text.replace("url = \"https://old.example/gone-1.0.jar\"", "# stale\nurl = \"https://old.example/gone-1.0.jar\"")
		val closures = mapOf("1.18.2-1" to listOf(artifact("demo:protocol:1.18.2-1")), "1.20.6-1" to listOf(artifact("demo:protocol:1.20.6-1")))

		val comment = assertThrows<GradleException> { ReleaseArtifactTables.replace(commented, closures) }
		val table = assertThrows<GradleException> { ReleaseArtifactTables.replace("[metadata]\n$text", closures) }
		val missing = assertThrows<GradleException> { ReleaseArtifactTables.replace(text, closures - "1.20.6-1") }

		assertTrue(comment.message!!.contains("Release 1.18.2-1 has a comment inside its [[release.artifact]] tables"), comment.message)
		assertTrue(table.message!!.contains("unexpected table '[metadata]'"), table.message)
		assertTrue(missing.message!!.contains("no resolved closure for release '1.20.6-1'"), missing.message)
	}

	@Test
	fun `refuses inline comments after artifact values and headers but not hashes inside strings`() {
		val closures = mapOf("1.18.2-1" to listOf(artifact("demo:protocol:1.18.2-1")), "1.20.6-1" to listOf(artifact("demo:protocol:1.20.6-1")))
		val value = text.replace("url = \"https://old.example/gone-1.0.jar\"", "url = \"https://old.example/gone-1.0.jar\" # stale")
		val header = text.replaceFirst("[[release.artifact]]", "[[release.artifact]] # generated")
		val hashed = text.replace("https://old.example/gone-1.0.jar", "https://old.example/gone-1.0.jar#fragment")

		val valueComment = assertThrows<GradleException> { ReleaseArtifactTables.replace(value, closures) }
		val headerComment = assertThrows<GradleException> { ReleaseArtifactTables.replace(header, closures) }

		assertTrue(valueComment.message!!.contains("move 'url = \"https://old.example/gone-1.0.jar\" # stale'"), valueComment.message)
		assertTrue(headerComment.message!!.contains("move '[[release.artifact]] # generated'"), headerComment.message)
		assertEquals(ReleaseArtifactTables.replace(text, closures), ReleaseArtifactTables.replace(hashed, closures))
	}

	@Test
	fun `keeps the layout of comments that lead into the next release or end the file`() {
		val laidOut = text.replace("# Snapshot releases follow.\n", "# Snapshot releases follow.\n\n# They resolve from SNAPSHOT folders.\n\n") +
			"\n# End of the releases.\n\n"
		val closures = mapOf("1.18.2-1" to listOf(artifact("demo:protocol:1.18.2-1")), "1.20.6-1" to listOf(artifact("demo:protocol:1.20.6-1")))

		val replaced = ReleaseArtifactTables.replace(laidOut, closures)

		assertTrue(replaced.contains("sha256 = \"${"a".repeat(64)}\"\n\n# Snapshot releases follow.\n\n# They resolve from SNAPSHOT folders.\n\n[[release]]"), replaced)
		assertTrue(replaced.endsWith("sha256 = \"${"a".repeat(64)}\"\n\n# End of the releases.\n"), replaced)
	}

	private fun artifact(module: String) = ReleaseArtifact(module, "file:/repository/$module.jar", "a".repeat(64))
}
