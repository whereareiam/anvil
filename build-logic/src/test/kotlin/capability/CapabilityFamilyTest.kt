package me.whereareiam.anvil.buildlogic.capability

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CapabilityFamilyTest {
	@TempDir
	lateinit var root: File

	@Test
	fun `reads the api, common and library sides of a family`() {
		val family = family("movement", "movement-api", "movement-common", "movement-mcprotocol")
		File(family, "src/main/resources/META-INF/services").mkdirs()
		File(family, "docs").mkdirs()

		val read = CapabilityFamily.read(family, listOf("mcprotocol"))

		assertEquals(CapabilityFamily("movement", "movement-api", "movement-common", listOf("movement-mcprotocol")), read)
		assertEquals(listOf("movement-api", "movement-mcprotocol"), read.published)
	}

	@Test
	fun `lets a process family without common code keep its provider in the root`() {
		val family = family("console", "console-api", owner = "process")
		File(family, "src/main/java").mkdirs()

		val read = CapabilityFamily.read(family, listOf("mcprotocol"))

		assertNull(read.common)
		assertEquals(listOf<String>(), read.sides)
	}

	@Test
	fun `keeps code out of the root of a player family without common code`() {
		val family = family("server", "server-api")
		File(family, "src/main/java").mkdirs()

		val failure = assertThrows<GradleException> { CapabilityFamily.read(family, listOf("mcprotocol")) }

		assertTrue(failure.message!!.contains("move the code of its root to 'server-common'"), failure.message)
	}

	@Test
	fun `names the families of every owner group`() {
		family("movement", "movement-api")
		family("session", "session-api")
		family("console", "console-api", owner = "process")
		File(root, "player/messages/builtin-messages").mkdirs()
		File(root, "player/messages/builtin-messages/build.gradle.kts").writeText("")
		File(root, "player/notes").mkdirs()

		assertEquals(listOf("console", "messages", "movement", "session"), CapabilityFamily.names(root))
	}

	@Test
	fun `requires the family api`() {
		val family = family("session", "session-common")

		val failure = assertThrows<GradleException> { CapabilityFamily.read(family, listOf("mcprotocol")) }

		assertTrue(failure.message!!.contains("must contain its API project 'session-api'"), failure.message)
	}

	@Test
	fun `rejects children that are not family members or sides of registered libraries`() {
		listOf("movement-protocol", "movement-fixture", "mcprotocol-movement").forEach { child ->
			val family = family("movement", "movement-api", child)

			val failure = assertThrows<GradleException> { CapabilityFamily.read(family, listOf("mcprotocol")) }

			assertTrue(failure.message!!.contains("found '$child'"), failure.message)
			family.deleteRecursively()
		}
	}

	@Test
	fun `keeps code out of the root of a family with common code or library sides`() {
		listOf(listOf("server-api", "server-common"), listOf("server-api", "server-mcprotocol")).forEach { members ->
			val family = family("server", *members.toTypedArray(), owner = "process")
			File(family, "src/main/java").mkdirs()

			val failure = assertThrows<GradleException> { CapabilityFamily.read(family, listOf("mcprotocol")) }

			assertTrue(failure.message!!.contains("move the code of its root to 'server-common'"), failure.message)
			family.deleteRecursively()
		}
	}

	@Test
	fun `keeps family code out of catch-all packages`() {
		listOf(
			"movement-common/src/main/java/me/example/movement/common",
			"movement-common/src/test/java/me/example/movement/common",
			"movement-mcprotocol/src/main/java/me/example/movement/util",
		).forEach { packageDirectory ->
			val family = family("movement", "movement-api", "movement-common", "movement-mcprotocol")
			File(family, "movement-common/src/main/java/me/example/movement").mkdirs()
			File(family, packageDirectory).mkdirs()

			val failure = assertThrows<GradleException> { CapabilityFamily.read(family, listOf("mcprotocol")) }

			val expected = packageDirectory.substringAfter("/java/").replace('/', '.')
			assertTrue(failure.message!!.contains("catch-all package '$expected' in '${packageDirectory.substringBefore('/')}'"), failure.message)
			family.deleteRecursively()
		}
	}

	private fun family(name: String, vararg projects: String, owner: String = "player"): File {
		val family = File(root, "$owner/$name")
		family.mkdirs()
		File(family, "build.gradle.kts").writeText("")
		projects.forEach { project ->
			File(family, project).mkdirs()
			File(family, "$project/build.gradle.kts").writeText("")
		}
		return family
	}
}
