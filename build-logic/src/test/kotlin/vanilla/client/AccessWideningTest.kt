package me.whereareiam.anvil.buildlogic.vanilla.client

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class AccessWideningTest {
	@TempDir
	lateinit var directory: File

	private val list = AccessList.parse("""
		public a.Outer${'$'}Inner
		public-f a.Outer
		public a.Outer hidden:I
		protected a.Outer helper()V
		public-f a.Outer constant:I
	""".trimIndent(), "family.cfg")

	@Test
	fun `widens exactly the listed classes and members and nothing else`() {
		val widened = widen(list)

		val outer = access(widened, "a/Outer")
		assertEquals(Opcodes.ACC_PUBLIC, outer.getValue("class") and VISIBILITY)
		assertEquals(0, outer.getValue("class") and Opcodes.ACC_FINAL)
		assertEquals(Opcodes.ACC_PUBLIC, outer.getValue("hidden:I"))
		assertEquals(Opcodes.ACC_PROTECTED, outer.getValue("helper()V"))
		assertEquals(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, outer.getValue("constant:I"))
		assertEquals(Opcodes.ACC_PRIVATE, outer.getValue("untouched:I"), "a member the list does not name keeps its access")
		assertEquals(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, outer.getValue("inner a/Outer\$Inner"))
		assertEquals(Opcodes.ACC_PUBLIC, access(widened, "a/Outer\$Inner").getValue("class") and VISIBILITY)
		assertEquals(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, access(widened, "a/Outer\$Inner").getValue("inner a/Outer\$Inner"))
		assertArrayEquals(entry(client(), "a/Other.class"), entry(widened, "a/Other.class"))
	}

	@Test
	fun `drops the signature, keeps the manifest's main attributes and writes the same bytes every time`() {
		val first = widen(list, "first.jar")
		val second = widen(list, "second.jar")

		val names = ZipFile(first).use { jar -> jar.entries().asSequence().map { it.name }.toList() }
		assertEquals(listOf("META-INF/MANIFEST.MF", "a/Outer.class", "a/Outer\$Inner.class", "a/Other.class"), names)
		val manifest = Manifest(entry(first, "META-INF/MANIFEST.MF").inputStream())
		assertEquals("net.minecraft.client.main.Main", manifest.mainAttributes.getValue("Main-Class"))
		assertTrue(manifest.entries.isEmpty())
		ZipFile(first).use { jar -> jar.entries().asSequence().forEach { assertEquals(315_532_800_000L, it.time) } }
		assertArrayEquals(first.readBytes(), second.readBytes())
	}

	@Test
	fun `refuses a list that names what the client does not have`() {
		val missing = AccessList.parse("public a.Outer gone:J\npublic a.Missing", "release.cfg")

		val failure = assertThrows<GradleException> { widen(missing) }

		assertEquals("client.jar has no a.Outer gone:J, a.Missing, which the access list widens", failure.message)
	}

	private fun widen(list: AccessList, name: String = "widened.jar"): File =
		File(directory, name).also { AccessWidening(list).widen(client(), it) }

	/**
	 * A client of a final package-private class with private members, a private nested class and an untouched class,
	 * signed, so its manifest digests its entries.
	 */
	private fun client(): File {
		val jar = File(directory, "client.jar")
		if (jar.exists()) return jar
		ZipOutputStream(jar.outputStream()).use { out ->
			out.write("META-INF/MANIFEST.MF", """
				Manifest-Version: 1.0
				Main-Class: net.minecraft.client.main.Main

				Name: a/Outer.class
				SHA-256-Digest: AAAA

			""".trimIndent().replace("\n", "\r\n").toByteArray())
			out.write("META-INF/MOJANGCS.SF", "Signature-Version: 1.0\r\n".toByteArray())
			out.write("META-INF/MOJANGCS.RSA", byteArrayOf(1, 2, 3))
			out.write("a/Outer.class", outer())
			out.write("a/Outer\$Inner.class", inner())
			out.write("a/Other.class", type("a/Other", Opcodes.ACC_SUPER))
		}
		return jar
	}

	private fun outer(): ByteArray = ClassWriter(0).apply {
		visit(Opcodes.V11, Opcodes.ACC_FINAL or Opcodes.ACC_SUPER, "a/Outer", null, "java/lang/Object", null)
		visitInnerClass("a/Outer\$Inner", "a/Outer", "Inner", Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC)
		visitField(Opcodes.ACC_PRIVATE, "hidden", "I", null, null).visitEnd()
		visitField(Opcodes.ACC_PRIVATE, "untouched", "I", null, null).visitEnd()
		visitField(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL, "constant", "I", null, 1).visitEnd()
		visitMethod(Opcodes.ACC_PRIVATE, "helper", "()V", null, null).apply {
			visitCode()
			visitInsn(Opcodes.RETURN)
			visitMaxs(0, 1)
			visitEnd()
		}
		visitEnd()
	}.toByteArray()

	private fun inner(): ByteArray = ClassWriter(0).apply {
		visit(Opcodes.V11, Opcodes.ACC_SUPER, "a/Outer\$Inner", null, "java/lang/Object", null)
		visitInnerClass("a/Outer\$Inner", "a/Outer", "Inner", Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC)
		visitEnd()
	}.toByteArray()

	private fun type(name: String, access: Int): ByteArray = ClassWriter(0).apply {
		visit(Opcodes.V11, access, name, null, "java/lang/Object", null)
		visitEnd()
	}.toByteArray()

	/**
	 * The access of a class, its members and the `InnerClasses` entries it lists, by `class`, the member as the list
	 * writes it, and `inner <name>`.
	 */
	private fun access(jar: File, name: String): Map<String, Int> {
		val access = mutableMapOf<String, Int>()
		ClassReader(entry(jar, "$name.class")).accept(object : ClassVisitor(Opcodes.ASM9) {
			override fun visit(version: Int, flags: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
				access["class"] = flags
			}

			override fun visitInnerClass(name: String, outerName: String?, innerName: String?, flags: Int) {
				access["inner $name"] = flags
			}

			override fun visitField(flags: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
				access["$name:$descriptor"] = flags
				return null
			}

			override fun visitMethod(flags: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
				access["$name$descriptor"] = flags
				return null
			}
		}, 0)
		return access
	}

	private fun entry(jar: File, name: String): ByteArray = ZipFile(jar).use { zip -> zip.getInputStream(zip.getEntry(name)).readBytes() }

	private fun ZipOutputStream.write(name: String, bytes: ByteArray) {
		putNextEntry(ZipEntry(name))
		write(bytes)
		closeEntry()
	}

	private companion object {
		const val VISIBILITY = Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED or Opcodes.ACC_PRIVATE
	}
}
