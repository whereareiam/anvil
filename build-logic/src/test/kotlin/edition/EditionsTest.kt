package me.whereareiam.anvil.buildlogic.edition

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EditionsTest {
	@TempDir
	lateinit var root: File

	@Test
	fun `names each edition folder`() {
		File(root, "anvil-edition/edition-java/java-protocol/protocol-api").mkdirs()
		File(root, "anvil-edition/edition-bedrock").mkdirs()
		File(root, "anvil-edition/notes").mkdirs()

		assertEquals(listOf("bedrock", "java"), Editions.discover(root))
	}

	@Test
	fun `names the project of an edition's protocol api`() {
		assertEquals(":anvil-edition:edition-java:java-protocol:protocol-api", Editions.apiPath("java"))
	}

	@Test
	fun `finds no edition in a build without editions`() {
		assertEquals(listOf<String>(), Editions.discover(root))
	}

	@Test
	fun `tells the projects below the editions folder`() {
		assertTrue(Editions.contains(":anvil-edition:edition-java:java-data:data-block"))
		assertFalse(Editions.contains(":anvil-player:player-api"))
	}

	@Test
	fun `derives a member's family and artifact id from its folders`() {
		assertEquals(":anvil-edition:edition-java", Editions.family(":anvil-edition:edition-java:java-data:data-block"))
		assertEquals("edition-java-data-block", Editions.artifactId(":anvil-edition:edition-java:java-data:data-block"))
		assertEquals("edition-java-protocol-api", Editions.artifactId(":anvil-edition:edition-java:java-protocol:protocol-api"))
	}

	@Test
	fun `refuses a project that is not laid out as an edition member`() {
		assertThrows<GradleException> { Editions.artifactId(":anvil-edition:edition-java:java-data") }
		assertThrows<GradleException> { Editions.artifactId(":anvil-edition:edition-java:java-data:block") }
		assertThrows<GradleException> { Editions.artifactId(":anvil-edition:edition-java:data:data-block") }
		assertThrows<GradleException> { Editions.family(":anvil-player:player-library:library-mcprotocol:mcprotocol-api") }
	}
}
