package me.whereareiam.anvil.buildlogic.segment.linkage

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * The linkable shape of one class: its access flags, its supertypes, and the fields and methods it declares with
 * their access flags.
 *
 * @property access the class's access flags, such as `ACC_PUBLIC` and `ACC_INTERFACE`
 * @property supertypes superclass first, then interfaces, as internal names
 * @property fields access flags of the declared fields, keyed by `name:descriptor`
 * @property methods access flags of the declared methods, keyed by `name(parameters)return`
 */
data class ClassHeader(
	val access: Int,
	val supertypes: List<String>,
	val fields: Map<String, Int>,
	val methods: Map<String, Int>,
) {
	/**
	 * Whether the class is an interface.
	 */
	val isInterface: Boolean
		get() = access and Opcodes.ACC_INTERFACE != 0

	/**
	 * Access flags of what [reference] names when this class declares it: the class's own flags for a class
	 * reference, otherwise the member's flags, or `null` when the class does not declare the member.
	 */
	fun accessOf(reference: LinkageReference): Int? = when (reference.kind) {
		LinkageReference.Kind.CLASS -> access
		LinkageReference.Kind.FIELD -> fields[reference.member]
		LinkageReference.Kind.METHOD -> methods[reference.member]
	}

	/**
	 * Whether this class itself declares the member [reference] names.
	 */
	fun declares(reference: LinkageReference): Boolean = accessOf(reference) != null

	companion object {
		/**
		 * Reads the header of a class file.
		 */
		fun read(reader: ClassReader): ClassHeader {
			val fields = mutableMapOf<String, Int>()
			val methods = mutableMapOf<String, Int>()
			reader.accept(object : ClassVisitor(Opcodes.ASM9) {
				override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
					fields["$name:$descriptor"] = access
					return null
				}

				override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
					methods[name + descriptor] = access
					return null
				}
			}, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
			return ClassHeader(reader.access, listOfNotNull(reader.superName) + reader.interfaces, fields, methods)
		}
	}
}
