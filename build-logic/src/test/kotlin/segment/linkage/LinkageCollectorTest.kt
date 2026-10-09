package me.whereareiam.anvil.buildlogic.segment.linkage

import me.whereareiam.anvil.buildlogic.segment.linkage.LinkageReference.Kind
import me.whereareiam.anvil.buildlogic.segment.linkage.LinkageReference.Requirement
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LinkageCollectorTest {
	@TempDir
	lateinit var directory: File

	private lateinit var fixture: LinkageFixture

	@BeforeEach
	fun compileFixture() {
		fixture = LinkageFixture(directory)
	}

	@AfterEach
	fun closeFixture() {
		fixture.close()
	}

	@Test
	fun `collects library references with their requirements, without the JDK, own classes or project dependencies`() {
		val references = fixture.externalReferences().map(LinkageReference::line).sorted()

		assertEquals(
			listOf(
				"class lib.Api public class",
				"class lib.Base public class",
				"class lib.Handler public",
				"class lib.Listener public interface",
				"field lib.Api version:Ljava/lang/String; public static",
				"method lib.Api <init>()V public instance class",
				"method lib.Api count()I public static class",
				"method lib.Api inherited()V public instance class",
				"method lib.Api kept()V public instance class",
				"method lib.Api narrowed()V public instance class",
				"method lib.Api removed()V public instance class",
				"method lib.Base <init>()V public instance class",
				"method lib.Base helper()V protected instance",
				"method lib.Handler handle()V public instance interface",
				"method lib.Listener onEvent()V public instance",
			),
			references,
		)
	}

	@Test
	fun `attributes an inherited member used through an own class to the library class declaring it`() {
		val references = fixture.externalReferences()

		assertTrue(references.contains(LinkageReference(Kind.METHOD, "lib/Listener", "onEvent()V", setOf(Requirement.PUBLIC, Requirement.INSTANCE))))
		assertTrue(references.contains(LinkageReference(Kind.METHOD, "lib/Base", "helper()V", setOf(Requirement.PROTECTED, Requirement.INSTANCE))))
	}

	@Test
	fun `manifest lines read back as the same references`() {
		val references = fixture.externalReferences()

		assertEquals(references, references.map { LinkageReference.parse(it.line) }.toSet())
	}

	@Test
	fun `rejects unknown requirements`() {
		val failure = assertThrows<IllegalArgumentException> { LinkageReference.parse("method lib.Api kept()V public final") }

		assertTrue(failure.message!!.contains("unknown requirement 'final'"), failure.message)
	}
}
