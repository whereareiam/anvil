package me.whereareiam.anvil.buildlogic.segment.linkage

import me.whereareiam.anvil.buildlogic.segment.linkage.LinkageReference.Kind
import me.whereareiam.anvil.buildlogic.segment.linkage.LinkageReference.Requirement
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Collects what a set of compiled classes links against outside themselves: supertypes, declared member
 * types, instruction operands, class and method-handle constants, bootstrap methods and caught exceptions.
 * Annotations and generic signatures are skipped because the JVM does not link them.
 *
 * Each reference records what its instruction requires: whether a member is linked as static or instance
 * (`getstatic` and `invokestatic` against the others) and whether its owner must be a class or an interface
 * (a class or interface method reference, a superclass or an implemented interface).
 */
class LinkageCollector {
	private val headers = mutableMapOf<String, ClassHeader>()
	private val references = sortedSetOf<LinkageReference>()

	/**
	 * Internal names of the collected classes.
	 */
	val classNames: Set<String>
		get() = headers.keys

	/**
	 * Internal names of every class the collected classes reference, JDK and collected classes included.
	 */
	val referencedClasses: Set<String>
		get() = references.mapTo(sortedSetOf(), LinkageReference::owner)

	/**
	 * Adds one class file read from [origin].
	 */
	fun add(classFile: ByteArray, origin: String) {
		val reader = ClassFiles.reader(classFile, origin)
		headers[reader.className] = ClassHeader.read(reader)
		reader.accept(ClassReferences(), ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
	}

	/**
	 * References to classes outside the collected ones, each with the access that [classpath] grants it.
	 *
	 * A member used through a collected class but inherited from an external supertype is attributed to the
	 * external class that declares it on [classpath], which is where the JVM resolves it; when no class there
	 * declares it, to the first external non-JDK supertype. Such a reference keeps its static or instance
	 * requirement, while the collected class it was named through carries the class or interface requirement.
	 * References to one class are merged into one line.
	 */
	fun externalReferences(classpath: ClassIndex): Set<LinkageReference> {
		val external = references.mapNotNull { reference ->
			when {
				reference.owner !in headers -> reference
				reference.kind == Kind.CLASS -> null
				else -> inheritedFrom(reference, classpath)?.let { owner ->
					reference.copy(owner = owner, requirements = reference.requirements - setOf(Requirement.CLASS, Requirement.INTERFACE))
				}
			}
		}
		val classes = external.filter { it.kind == Kind.CLASS }
			.groupBy(LinkageReference::owner)
			.map { (owner, uses) -> LinkageReference(Kind.CLASS, owner, requirements = uses.flatMap(LinkageReference::requirements).toSet()) }
		return (classes + external.filter { it.kind != Kind.CLASS })
			.map { reference -> classpath.accessOf(reference)?.let { reference.copy(requirements = reference.requirements + it) } ?: reference }
			.toSortedSet()
	}

	private fun inheritedFrom(reference: LinkageReference, classpath: ClassIndex): String? {
		val external = mutableListOf<String>()
		val visited = mutableSetOf<String>()
		fun declares(type: String): Boolean {
			if (!visited.add(type)) return false
			val header = headers[type]
			if (header == null) {
				external += type
				return false
			}
			return header.declares(reference) || header.supertypes.any(::declares)
		}
		if (declares(reference.owner)) return null
		return external.firstNotNullOfOrNull { classpath.declaringClass(reference.copy(owner = it)) }
			?: external.firstOrNull { !classpath.jdk.contains(it) }
	}

	private fun addClass(internalName: String, vararg requirements: Requirement) {
		references += LinkageReference(Kind.CLASS, internalName, requirements = requirements.toSet())
	}

	private fun addType(type: Type) {
		when (type.sort) {
			Type.OBJECT -> addClass(type.internalName)
			Type.ARRAY -> addType(type.elementType)
			Type.METHOD -> {
				type.argumentTypes.forEach(::addType)
				addType(type.returnType)
			}
		}
	}

	private fun addField(owner: String, name: String, descriptor: String, static: Boolean) {
		addType(Type.getType(descriptor))
		if (owner.startsWith("[")) return
		references += LinkageReference(Kind.FIELD, owner, "$name:$descriptor", setOf(if (static) Requirement.STATIC else Requirement.INSTANCE))
	}

	private fun addMethod(owner: String, name: String, descriptor: String, static: Boolean, ownerIsInterface: Boolean) {
		addType(Type.getMethodType(descriptor))
		if (owner.startsWith("[")) return
		references += LinkageReference(
			Kind.METHOD,
			owner,
			name + descriptor,
			setOf(
				if (static) Requirement.STATIC else Requirement.INSTANCE,
				if (ownerIsInterface) Requirement.INTERFACE else Requirement.CLASS,
			),
		)
	}

	private fun addConstant(value: Any?) {
		when (value) {
			is Type -> addType(value)
			is Handle -> addHandle(value)
			is ConstantDynamic -> {
				addType(Type.getType(value.descriptor))
				addHandle(value.bootstrapMethod)
				(0 until value.bootstrapMethodArgumentCount).forEach { addConstant(value.getBootstrapMethodArgument(it)) }
			}
		}
	}

	private fun addHandle(handle: Handle) {
		when (handle.tag) {
			Opcodes.H_GETSTATIC, Opcodes.H_PUTSTATIC -> addField(handle.owner, handle.name, handle.desc, static = true)
			Opcodes.H_GETFIELD, Opcodes.H_PUTFIELD -> addField(handle.owner, handle.name, handle.desc, static = false)
			Opcodes.H_INVOKESTATIC -> addMethod(handle.owner, handle.name, handle.desc, static = true, handle.isInterface)
			else -> addMethod(handle.owner, handle.name, handle.desc, static = false, handle.isInterface)
		}
	}

	private inner class ClassReferences : ClassVisitor(Opcodes.ASM9) {
		override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
			superName?.let { addClass(it, Requirement.CLASS) }
			interfaces?.forEach { addClass(it, Requirement.INTERFACE) }
		}

		override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
			addType(Type.getType(descriptor))
			return null
		}

		override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
			addType(Type.getMethodType(descriptor))
			exceptions?.forEach { addClass(it) }
			return MethodReferences()
		}
	}

	private inner class MethodReferences : MethodVisitor(Opcodes.ASM9) {
		override fun visitTypeInsn(opcode: Int, type: String) {
			if (opcode == Opcodes.NEW) {
				addClass(type, Requirement.CLASS)
				return
			}
			addType(Type.getObjectType(type))
		}

		override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
			addField(owner, name, descriptor, static = opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC)
		}

		override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
			addMethod(owner, name, descriptor, static = opcode == Opcodes.INVOKESTATIC, isInterface)
		}

		override fun visitInvokeDynamicInsn(name: String, descriptor: String, bootstrapMethodHandle: Handle, vararg bootstrapMethodArguments: Any?) {
			addType(Type.getMethodType(descriptor))
			addHandle(bootstrapMethodHandle)
			bootstrapMethodArguments.forEach(::addConstant)
		}

		override fun visitLdcInsn(value: Any?) {
			addConstant(value)
		}

		override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
			addType(Type.getType(descriptor))
		}

		override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
			type?.let { addClass(it) }
		}
	}
}
