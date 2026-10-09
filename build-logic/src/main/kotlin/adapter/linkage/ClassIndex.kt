package me.whereareiam.anvil.buildlogic.adapter.linkage

import me.whereareiam.anvil.buildlogic.adapter.linkage.LinkageReference.Kind
import me.whereareiam.anvil.buildlogic.adapter.linkage.LinkageReference.Requirement
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile

/**
 * The class headers of one classpath on top of a JDK, resolving linkage references the way the JVM does: a member
 * may be declared by the named class or inherited from its superclasses and interfaces, including JDK supertypes.
 *
 * @property jdk the JDK the classpath runs on
 */
class ClassIndex private constructor(private val headers: Map<String, ClassHeader>, val jdk: JdkClasses) {
	/**
	 * Internal names of the classpath's classes, without the JDK.
	 */
	val classNames: Set<String>
		get() = headers.keys

	/**
	 * Whether the classpath itself contains [className].
	 */
	operator fun contains(className: String): Boolean = className in headers

	/**
	 * Describes why [reference] does not link on this classpath: a missing class or member, a class that changed
	 * between class and interface, a member that changed between static and instance, or a narrower access than
	 * the reference requires.
	 *
	 * @return the failure, or `null` when the reference links
	 */
	fun linkageFailure(reference: LinkageReference): String? {
		val owner = reference.owner.replace('/', '.')
		val header = header(reference.owner) ?: return "missing class $owner"
		if (Requirement.CLASS in reference.requirements && header.isInterface) return "$owner is an interface, linked as a class"
		if (Requirement.INTERFACE in reference.requirements && !header.isInterface) return "$owner is a class, linked as an interface"

		val described = if (reference.kind == Kind.CLASS) "class $owner" else "${reference.kind.keyword} $owner ${reference.member}"
		val declaring = declaringClass(reference) ?: return "missing $described"
		val access = header(declaring)!!.accessOf(reference)!!
		val static = access and Opcodes.ACC_STATIC != 0
		if (Requirement.STATIC in reference.requirements && !static) return "$described is not static, linked as static"
		if (Requirement.INSTANCE in reference.requirements && static) return "$described is static, linked as instance"
		if (Requirement.PUBLIC in reference.requirements && access and Opcodes.ACC_PUBLIC == 0) return "$described is no longer public"
		if (Requirement.PROTECTED in reference.requirements && access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) == 0)
			return "$described is no longer protected"
		return null
	}

	/**
	 * The least access the classpath grants to what [reference] names: [Requirement.PUBLIC], [Requirement.PROTECTED],
	 * or `null` when it is neither or does not resolve.
	 */
	fun accessOf(reference: LinkageReference): Requirement? {
		val declaring = declaringClass(reference) ?: return null
		val access = header(declaring)!!.accessOf(reference)!!
		if (access and Opcodes.ACC_PUBLIC != 0) return Requirement.PUBLIC
		if (access and Opcodes.ACC_PROTECTED != 0) return Requirement.PROTECTED
		return null
	}

	/**
	 * Finds the class that declares the member [reference] names, starting at its owner and searching the
	 * superclasses before the interfaces. A class reference is declared by its owner.
	 *
	 * @return internal name of the declaring class, or `null` when the member does not resolve
	 */
	fun declaringClass(reference: LinkageReference): String? = declaringClass(reference, reference.owner, mutableSetOf())

	private fun declaringClass(reference: LinkageReference, type: String, visited: MutableSet<String>): String? {
		if (!visited.add(type)) return null
		val header = header(type) ?: return null
		if (header.declares(reference)) return type
		return header.supertypes.firstNotNullOfOrNull { declaringClass(reference, it, visited) }
	}

	private fun header(className: String): ClassHeader? = headers[className] ?: jdk.header(className)

	companion object {
		/**
		 * Indexes JARs and class directories on top of [jdk]. Like a classpath, the first entry that defines a class
		 * wins.
		 */
		fun of(files: Iterable<File>, jdk: JdkClasses): ClassIndex {
			val headers = linkedMapOf<String, ClassHeader>()
			fun add(bytes: ByteArray, origin: String) {
				val reader = ClassFiles.reader(bytes, origin)
				headers.putIfAbsent(reader.className, ClassHeader.read(reader))
			}
			for (file in files) {
				if (file.isDirectory) {
					file.walkTopDown().filter(::isClassFile).forEach { add(it.readBytes(), it.path) }
					continue
				}
				if (!file.isFile || !file.name.endsWith(".jar")) continue
				ZipFile(file).use { jar ->
					jar.entries().asSequence()
						.filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.startsWith("META-INF/") && !it.name.endsWith("module-info.class") }
						.forEach { entry -> jar.getInputStream(entry).use { add(it.readBytes(), "${file.name}!/${entry.name}") } }
				}
			}
			return ClassIndex(headers, jdk)
		}

		private fun isClassFile(file: File): Boolean = file.isFile && file.name.endsWith(".class") && file.name != "module-info.class"
	}
}
