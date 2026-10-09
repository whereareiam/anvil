package me.whereareiam.anvil.buildlogic.adapter.linkage

import me.whereareiam.anvil.buildlogic.adapter.linkage.LinkageReference.Kind
import me.whereareiam.anvil.buildlogic.adapter.linkage.LinkageReference.Requirement
import org.gradle.api.GradleException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ClassIndexTest {
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
	fun `links every reference on the release the segment compiled against`() {
		val index = ClassIndex.of(listOf(fixture.firstRelease), fixture.jdk)

		assertEquals(emptyList<String>(), fixture.externalReferences().mapNotNull(index::linkageFailure))
	}

	@Test
	fun `reports missing members, static and interface changes and narrower access on a later release`() {
		val index = ClassIndex.of(listOf(fixture.laterRelease), fixture.jdk)

		assertEquals(
			listOf(
				"method lib.Api count()I is not static, linked as static",
				"method lib.Api narrowed()V is no longer public",
				"missing method lib.Api removed()V",
				"lib.Handler is a class, linked as an interface",
			),
			fixture.externalReferences().mapNotNull(index::linkageFailure),
		)
		assertEquals("missing class lib.Gone", index.linkageFailure(LinkageReference(Kind.CLASS, "lib/Gone")))
	}

	@Test
	fun `reports an interface linked as a class, a static member linked as instance and a protected member made private`() {
		val first = ClassIndex.of(listOf(fixture.firstRelease), fixture.jdk)
		val privateRelease = ClassIndex.of(listOf(fixture.compile("private", emptyList(), "package lib; public class Base { private void helper() {} }")), fixture.jdk)

		assertEquals(
			"lib.Listener is an interface, linked as a class",
			first.linkageFailure(LinkageReference(Kind.CLASS, "lib/Listener", requirements = setOf(Requirement.CLASS))),
		)
		assertEquals(
			"method lib.Api count()I is static, linked as instance",
			first.linkageFailure(LinkageReference(Kind.METHOD, "lib/Api", "count()I", setOf(Requirement.INSTANCE))),
		)
		assertEquals(
			"method lib.Base helper()V is no longer protected",
			privateRelease.linkageFailure(LinkageReference(Kind.METHOD, "lib/Base", "helper()V", setOf(Requirement.PROTECTED, Requirement.INSTANCE))),
		)
	}

	@Test
	fun `resolves members inherited from library and JDK supertypes`() {
		val index = ClassIndex.of(listOf(fixture.laterRelease), fixture.jdk)

		assertEquals("lib/Base", index.declaringClass(LinkageReference(Kind.METHOD, "lib/Api", "inherited()V")))
		assertEquals("java/lang/Object", index.declaringClass(LinkageReference(Kind.METHOD, "lib/Api", "hashCode()I")))
		assertNull(index.linkageFailure(LinkageReference(Kind.METHOD, "lib/Api", "toString()Ljava/lang/String;")))
		assertEquals(
			"missing field lib.Api NAME:Ljava/lang/String;",
			index.linkageFailure(LinkageReference(Kind.FIELD, "lib/Api", "NAME:Ljava/lang/String;")),
		)
	}

	@Test
	fun `reads JDK classes from every module of the given installation`() {
		assertTrue(fixture.jdk.contains("java/lang/String"))
		assertTrue(fixture.jdk.contains("java/sql/Connection"))
		assertTrue(fixture.jdk.header("java/util/List")!!.isInterface)
		assertFalse(fixture.jdk.contains("lib/Api"))
		assertFalse(fixture.jdk.contains("Unnamed"))
	}

	@Test
	fun `names a class file newer than the linkage tools read`() {
		val classes = File(directory, "future")
		val api = File(fixture.firstRelease, "lib/Api.class").readBytes()
		api[6] = 0
		api[7] = 99
		File(classes, "lib/Api.class").apply {
			parentFile.mkdirs()
			writeBytes(api)
		}

		val failure = assertThrows<GradleException> { ClassIndex.of(listOf(classes), fixture.jdk) }

		assertTrue(failure.message!!.contains("lib/Api.class has class file version 99 (Java 55)"), failure.message)
		assertTrue(failure.message!!.contains("update 'asm' in gradle/libs.versions.toml"), failure.message)
	}
}
