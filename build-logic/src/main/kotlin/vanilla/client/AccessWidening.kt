package me.whereareiam.anvil.buildlogic.vanilla.client

import org.gradle.api.GradleException
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Widens the access of exactly the classes and members an [AccessList] names in a client JAR, so the harness reaches
 * them as plain, compiler-checked Java.
 *
 * The output is deterministic: it holds the input's entries in their order, every entry with the same fixed time, the
 * classes the list touches rewritten and every other entry's bytes as they were. It leaves out the JAR's signature,
 * which no longer matches, and keeps only the manifest's main attributes. A widened nested class is widened in the
 * `InnerClasses` attribute of every class that lists it as well, which the compiler reads its access from.
 *
 * @property list the classes and members to widen
 */
class AccessWidening(private val list: AccessList) {
	private val byOwner = list.byOwner()
	private val widenedClasses = list.entries.filter { it.member == null }.associateBy { it.owner }

	/**
	 * Writes [input] with the list's access widened as [output].
	 *
	 * @throws GradleException when the list names a class or member the JAR does not have
	 */
	fun widen(input: File, output: File) {
		val found = mutableSetOf<AccessList.Entry>()
		ZipFile(input).use { jar ->
			ZipOutputStream(output.outputStream().buffered()).use { out ->
				for (entry in jar.entries().asSequence()) {
					if (isSignature(entry.name)) continue
					val bytes = jar.getInputStream(entry).use { it.readBytes() }
					out.putNextEntry(ZipEntry(entry.name).apply { time = ENTRY_TIME })
					out.write(transform(entry.name, bytes, found))
					out.closeEntry()
				}
			}
		}
		val missing = list.entries - found
		if (missing.isNotEmpty())
			throw GradleException("${input.name} has no ${missing.joinToString { describe(it) }}, which the access list widens")
	}

	private fun transform(name: String, bytes: ByteArray, found: MutableSet<AccessList.Entry>): ByteArray {
		if (name == JarFile.MANIFEST_NAME) return mainAttributes(bytes)
		if (!name.endsWith(".class")) return bytes
		val owner = name.removeSuffix(".class")
		if (owner !in byOwner && !mentionsWidenedClass(bytes)) return bytes

		val reader = ClassReader(bytes)
		val writer = ClassWriter(reader, 0)
		reader.accept(Widener(writer, byOwner[owner].orEmpty(), found), 0)
		return writer.toByteArray()
	}

	/**
	 * Whether the class's constant pool names a widened nested class, the only way its `InnerClasses` can list one.
	 */
	private fun mentionsWidenedClass(bytes: ByteArray): Boolean {
		if (widenedClasses.isEmpty()) return false
		val text = String(bytes, Charsets.ISO_8859_1)
		return widenedClasses.keys.any { '$' in it && text.contains(it) }
	}

	private inner class Widener(
		delegate: ClassVisitor,
		entries: List<AccessList.Entry>,
		private val found: MutableSet<AccessList.Entry>,
	) : ClassVisitor(Opcodes.ASM9, delegate) {
		private val members = entries.filter { it.member != null }.associateBy { it.member!! }
		private val own = entries.firstOrNull { it.member == null }

		override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
			own?.let { found += it }
			super.visit(version, own?.let { widen(access, it) } ?: access, name, signature, superName, interfaces)
		}

		override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
			super.visitInnerClass(name, outerName, innerName, widenedClasses[name]?.let { widen(access, it) } ?: access)
		}

		override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
			val entry = members[AccessList.Member(name, descriptor, false)]?.also { found += it }
			return super.visitField(entry?.let { widen(access, it) } ?: access, name, descriptor, signature, value)
		}

		override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
			val entry = members[AccessList.Member(name, descriptor, true)]?.also { found += it }
			return super.visitMethod(entry?.let { widen(access, it) } ?: access, name, descriptor, signature, exceptions)
		}
	}

	private companion object {
		/**
		 * The time of every entry, the start of the ZIP format's epoch, so the output does not depend on when it was written.
		 */
		const val ENTRY_TIME = 315_532_800_000L
		const val VISIBILITY = Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED or Opcodes.ACC_PRIVATE

		/**
		 * Gives [access] the entry's access unless it already is public, and removes `final` when the entry says so.
		 */
		fun widen(access: Int, entry: AccessList.Entry): Int {
			val visibility = if (access and Opcodes.ACC_PUBLIC != 0) Opcodes.ACC_PUBLIC else entry.access.flag
			val widened = (access and VISIBILITY.inv()) or visibility
			return if (entry.definalize) widened and Opcodes.ACC_FINAL.inv() else widened
		}

		fun isSignature(name: String): Boolean {
			if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length) >= 0) return false
			val file = name.removePrefix("META-INF/")
			return file.startsWith("SIG-") || listOf(".SF", ".RSA", ".DSA", ".EC").any(file::endsWith)
		}

		/**
		 * The manifest with only its main attributes, without the digests of a signature it no longer has.
		 */
		fun mainAttributes(bytes: ByteArray): ByteArray {
			val main = Manifest().apply { mainAttributes.putAll(Manifest(bytes.inputStream()).mainAttributes) }
			return ByteArrayOutputStream().also(main::write).toByteArray()
		}

		fun describe(entry: AccessList.Entry): String = entry.owner.replace('/', '.') + (entry.member?.let { " $it" } ?: "")
	}
}
