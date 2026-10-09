package me.whereareiam.anvil.buildlogic

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * A Maven file repository of tiny protocol library releases that the tests generate with ASM, so the functional
 * tests need no real library and no network for it.
 *
 * Classes are described by internal name and members such as `public static count()I`, `public <init>(I)V` or
 * `public abstract handle()V`; every concrete method returns a default value.
 */
class LibraryRepository(val root: File) {
	private val uniqueSnapshot = Regex("""(.+)-(\d{8}\.\d{6})-(\d+)""")

	/**
	 * One generated class.
	 *
	 * @property name internal name, such as `com/example/Api`
	 * @property members member declarations: modifiers, then `name(parameters)return` or `name:descriptor`
	 * @property superName internal name of the superclass
	 * @property isInterface whether the class is an interface
	 */
	class LibraryClass(
		val name: String,
		val members: List<String> = emptyList(),
		val superName: String = "java/lang/Object",
		val isInterface: Boolean = false,
	)

	/**
	 * Publishes the JAR of [module] (`group:name:version[:classifier]`) with [classes]. A module without classifier
	 * also gets a POM declaring [dependencies] at compile scope; a unique snapshot version is published in its
	 * `-SNAPSHOT` folder with the metadata that resolves it.
	 */
	fun publish(module: String, classes: List<LibraryClass>, dependencies: List<String> = emptyList()) {
		val (group, name, version) = module.split(':')
		val classifier = module.split(':').getOrNull(3)
		val snapshot = uniqueSnapshot.matchEntire(version)
		val folder = File(root, "${group.replace('.', '/')}/$name/${snapshot?.let { it.groupValues[1] + "-SNAPSHOT" } ?: version}")
		folder.mkdirs()
		jar(File(folder, "$name-$version${classifier?.let { "-$it" }.orEmpty()}.jar"), classes)
		if (classifier != null) return

		File(folder, "$name-$version.pom").writeText(pom(group, name, snapshot?.let { it.groupValues[1] + "-SNAPSHOT" } ?: version, dependencies))
		snapshot?.let { File(folder, "maven-metadata.xml").writeText(snapshotMetadata(group, name, it)) }
	}

	private fun jar(file: File, classes: List<LibraryClass>) {
		JarOutputStream(file.outputStream()).use { output ->
			classes.forEach { type ->
				output.putNextEntry(JarEntry("${type.name}.class"))
				output.write(classFile(type))
				output.closeEntry()
			}
		}
	}

	private fun classFile(type: LibraryClass): ByteArray {
		val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
		val kind = if (type.isInterface) Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT else Opcodes.ACC_SUPER
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or kind, type.name, null, type.superName, null)
		type.members.forEach { member ->
			val words = member.split(' ')
			val access = words.dropLast(1).sumOf(::modifier)
			val signature = words.last()
			if ('(' !in signature) {
				writer.visitField(access, signature.substringBefore(':'), signature.substringAfter(':'), null, null).visitEnd()
				return@forEach
			}
			val methodName = signature.substringBefore('(')
			val descriptor = signature.substring(methodName.length)
			val method = writer.visitMethod(access, methodName, descriptor, null, null)
			if (access and Opcodes.ACC_ABSTRACT == 0) body(method, type, methodName, descriptor)
			method.visitEnd()
		}
		writer.visitEnd()
		return writer.toByteArray()
	}

	private fun body(method: MethodVisitor, type: LibraryClass, name: String, descriptor: String) {
		method.visitCode()
		if (name == "<init>") {
			method.visitVarInsn(Opcodes.ALOAD, 0)
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, type.superName, "<init>", "()V", false)
			method.visitInsn(Opcodes.RETURN)
		} else {
			val returnType = Type.getReturnType(descriptor)
			when (returnType.sort) {
				Type.VOID -> method.visitInsn(Opcodes.RETURN)
				Type.OBJECT, Type.ARRAY -> {
					method.visitInsn(Opcodes.ACONST_NULL)
					method.visitInsn(Opcodes.ARETURN)
				}
				Type.LONG -> {
					method.visitInsn(Opcodes.LCONST_0)
					method.visitInsn(Opcodes.LRETURN)
				}
				Type.FLOAT -> {
					method.visitInsn(Opcodes.FCONST_0)
					method.visitInsn(Opcodes.FRETURN)
				}
				Type.DOUBLE -> {
					method.visitInsn(Opcodes.DCONST_0)
					method.visitInsn(Opcodes.DRETURN)
				}
				else -> {
					method.visitInsn(Opcodes.ICONST_0)
					method.visitInsn(Opcodes.IRETURN)
				}
			}
		}
		method.visitMaxs(0, 0)
	}

	private fun modifier(word: String): Int = when (word) {
		"public" -> Opcodes.ACC_PUBLIC
		"protected" -> Opcodes.ACC_PROTECTED
		"static" -> Opcodes.ACC_STATIC
		"abstract" -> Opcodes.ACC_ABSTRACT
		else -> error("Unknown modifier '$word'")
	}

	private fun pom(group: String, name: String, version: String, dependencies: List<String>): String {
		val declared = dependencies.joinToString("") { dependency ->
			val parts = dependency.split(':')
			"<dependency><groupId>${parts[0]}</groupId><artifactId>${parts[1]}</artifactId><version>${parts[2]}</version>" +
				(parts.getOrNull(3)?.let { "<classifier>$it</classifier>" } ?: "") + "</dependency>"
		}
		return """<?xml version="1.0" encoding="UTF-8"?>
			|<project xmlns="http://maven.apache.org/POM/4.0.0">
			|<modelVersion>4.0.0</modelVersion>
			|<groupId>$group</groupId><artifactId>$name</artifactId><version>$version</version>
			|<dependencies>$declared</dependencies>
			|</project>
			|""".trimMargin()
	}

	private fun snapshotMetadata(group: String, name: String, snapshot: MatchResult): String {
		val (base, timestamp, build) = snapshot.destructured
		val value = "$base-$timestamp-$build"
		val updated = timestamp.replace(".", "")
		return """<?xml version="1.0" encoding="UTF-8"?>
			|<metadata><groupId>$group</groupId><artifactId>$name</artifactId><version>$base-SNAPSHOT</version>
			|<versioning><snapshot><timestamp>$timestamp</timestamp><buildNumber>$build</buildNumber></snapshot>
			|<lastUpdated>$updated</lastUpdated><snapshotVersions>
			|<snapshotVersion><extension>jar</extension><value>$value</value><updated>$updated</updated></snapshotVersion>
			|<snapshotVersion><extension>pom</extension><value>$value</value><updated>$updated</updated></snapshotVersion>
			|</snapshotVersions></versioning></metadata>
			|""".trimMargin()
	}
}
