package me.whereareiam.anvil.buildlogic.platform

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class AgentJarClassesTest {
	@TempDir
	lateinit var directory: File

	@Test
	fun `lists classes that need newer Java than the checked version`() {
		val jar = jar(
			"agent/Agent.class" to type("agent/Agent", Opcodes.V11),
			"agent/Modern.class" to type("agent/Modern", Opcodes.V17),
			"shaded/Legacy.class" to type("shaded/Legacy", Opcodes.V1_8),
		)

		assertEquals(listOf("agent.jar!agent/Modern.class needs Java 17"), AgentJarClasses.newerThan(jar, 11))
		assertEquals(emptyList<String>(), AgentJarClasses.newerThan(jar, 17))
	}

	@Test
	fun `checks only the multi-release entries the checked Java loads`() {
		val jar = jar(
			"module-info.class" to type("module-info", Opcodes.V21),
			"META-INF/versions/9/module-info.class" to type("module-info", Opcodes.V9),
			"META-INF/versions/11/shaded/Fast.class" to type("shaded/Fast", Opcodes.V17),
			"META-INF/versions/21/shaded/Fast.class" to type("shaded/Fast", Opcodes.V21),
		)

		assertEquals(
			listOf("agent.jar!META-INF/versions/11/shaded/Fast.class needs Java 17"),
			AgentJarClasses.newerThan(jar, 11),
		)
	}

	@Test
	fun `refuses an entry without a class-file header`() {
		val jar = jar("agent/Broken.class" to "not a class".toByteArray())

		val failure = assertThrows<GradleException> { AgentJarClasses.newerThan(jar, 11) }
		assertTrue(failure.message!!.contains("agent.jar!agent/Broken.class is not a class file"), failure.message)
	}

	private fun type(name: String, version: Int): ByteArray = ClassWriter(0).apply {
		visit(version, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
		visitEnd()
	}.toByteArray()

	private fun jar(vararg entries: Pair<String, ByteArray>): File {
		val jar = File(directory, "agent.jar")
		JarOutputStream(jar.outputStream()).use { output ->
			entries.forEach { (name, bytes) ->
				output.putNextEntry(JarEntry(name))
				output.write(bytes)
				output.closeEntry()
			}
		}
		return jar
	}
}
