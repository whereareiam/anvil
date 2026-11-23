package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference.Kind;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference.Requirement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Collects what a set of compiled classes links against outside themselves: supertypes, declared member types,
 * instruction operands, class and method-handle constants, bootstrap methods and caught exceptions. Annotations and
 * generic signatures are skipped because the JVM does not link them.
 *
 * <p>Each reference records what its instruction requires: whether a member is linked as static or instance
 * ({@code getstatic} and {@code invokestatic} against the others) and whether its owner must be a class or an
 * interface (a class or interface method reference, a superclass or an implemented interface).</p>
 */
public final class LinkageCollector {
	private final Map<String, ClassHeader> headers = new HashMap<>();
	private final SortedSet<LinkageReference> references = new TreeSet<>();

	/**
	 * Adds one class file.
	 *
	 * @param classFile bytes of the class file
	 * @param origin where it was read, for failure messages
	 * @throws IllegalArgumentException when the bytes are no class file ASM can read
	 */
	public void add(byte @NotNull [] classFile, @NotNull String origin) {
		ClassReader reader = ClassFiles.reader(classFile, origin);
		headers.put(reader.getClassName(), ClassHeader.read(reader));
		reader.accept(new ClassReferences(), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
	}

	/**
	 * Returns the internal names of the collected classes.
	 *
	 * @return unmodifiable names
	 */
	public @NotNull Set<String> classNames() {
		return Collections.unmodifiableSet(headers.keySet());
	}

	/**
	 * Returns the internal names of every class the collected classes reference, JDK and collected classes included.
	 *
	 * @return sorted names
	 */
	public @NotNull SortedSet<String> referencedClasses() {
		SortedSet<String> classes = new TreeSet<>();
		references.forEach(reference -> classes.add(reference.getOwner()));
		return classes;
	}

	/**
	 * Returns what a segment's linkage manifest lists: the references to classes outside the collected ones, the JDK
	 * and the packages of the build, which are those of the collected classes and of the project dependencies.
	 *
	 * @param classpath the segment's compile class path, see {@link #externalReferences(ClassPath)}
	 * @param projectDependencies the compiled project dependencies, whose packages belong to the build
	 * @return sorted references
	 */
	public @NotNull SortedSet<LinkageReference> segmentReferences(@NotNull ClassPath classpath, @NotNull ClassPath projectDependencies) {
		Set<String> buildPackages = new HashSet<>();
		headers.keySet().forEach(name -> buildPackages.add(packageOf(name)));
		projectDependencies.classNames().forEach(name -> buildPackages.add(packageOf(name)));

		SortedSet<LinkageReference> manifest = new TreeSet<>();
		for (LinkageReference reference : externalReferences(classpath)) {
			if (buildPackages.contains(packageOf(reference.getOwner())) || classpath.jdk().contains(reference.getOwner())) continue;

			manifest.add(reference);
		}
		return manifest;
	}

	/**
	 * Returns the references to classes outside the collected ones, each with the access that a class path grants it.
	 *
	 * <p>A member used through a collected class but inherited from an external supertype is attributed to the
	 * external class that declares it on the class path, which is where the JVM resolves it; when no class there
	 * declares it, to the first external non-JDK supertype. Such a reference keeps its static or instance requirement,
	 * while the collected class it was named through carries the class or interface requirement. References to one
	 * class are merged into one line.</p>
	 *
	 * @param classpath the class path the collected classes compiled against
	 * @return sorted references
	 */
	public @NotNull SortedSet<LinkageReference> externalReferences(@NotNull ClassPath classpath) {
		List<LinkageReference> external = new ArrayList<>();
		for (LinkageReference reference : references) {
			if (!headers.containsKey(reference.getOwner())) {
				external.add(reference);
				continue;
			}
			if (reference.getKind() == Kind.CLASS) continue;

			String owner = inheritedFrom(reference, classpath);
			if (owner == null) continue;

			Set<Requirement> requirements = EnumSet.noneOf(Requirement.class);
			requirements.addAll(reference.getRequirements());
			requirements.remove(Requirement.CLASS);
			requirements.remove(Requirement.INTERFACE);
			external.add(reference.with(owner, requirements));
		}

		Map<String, Set<Requirement>> classes = new LinkedHashMap<>();
		List<LinkageReference> merged = new ArrayList<>();
		for (LinkageReference reference : external) {
			if (reference.getKind() != Kind.CLASS) continue;

			classes.computeIfAbsent(reference.getOwner(), owner -> EnumSet.noneOf(Requirement.class)).addAll(reference.getRequirements());
		}
		classes.forEach((owner, requirements) -> merged.add(new LinkageReference(Kind.CLASS, owner, "", requirements)));
		external.stream().filter(reference -> reference.getKind() != Kind.CLASS).forEach(merged::add);

		SortedSet<LinkageReference> granted = new TreeSet<>();
		for (LinkageReference reference : merged) {
			Requirement access = classpath.accessOf(reference);
			if (access == null) {
				granted.add(reference);
				continue;
			}

			Set<Requirement> requirements = EnumSet.of(access);
			requirements.addAll(reference.getRequirements());
			granted.add(reference.with(reference.getOwner(), requirements));
		}
		return granted;
	}

	private @Nullable String inheritedFrom(LinkageReference reference, ClassPath classpath) {
		List<String> external = new ArrayList<>();
		if (declares(reference, reference.getOwner(), external, new HashSet<>())) return null;

		for (String type : external) {
			String declaring = classpath.declaringClass(reference.with(type, reference.getRequirements()));
			if (declaring != null) return declaring;
		}
		return external.stream().filter(type -> !classpath.jdk().contains(type)).findFirst().orElse(null);
	}

	private boolean declares(LinkageReference reference, String type, List<String> external, Set<String> visited) {
		if (!visited.add(type)) return false;

		ClassHeader header = headers.get(type);
		if (header == null) {
			external.add(type);
			return false;
		}
		if (header.declares(reference)) return true;

		for (String supertype : header.supertypes())
			if (declares(reference, supertype, external, visited)) return true;
		return false;
	}

	private static String packageOf(String className) {
		int separator = className.lastIndexOf('/');
		return separator < 0 ? "" : className.substring(0, separator);
	}

	private void addClass(String internalName, Requirement... requirements) {
		Set<Requirement> set = EnumSet.noneOf(Requirement.class);
		Collections.addAll(set, requirements);
		references.add(new LinkageReference(Kind.CLASS, internalName, "", set));
	}

	private void addType(Type type) {
		switch (type.getSort()) {
			case Type.OBJECT -> addClass(type.getInternalName());
			case Type.ARRAY -> addType(type.getElementType());
			case Type.METHOD -> {
				for (Type argument : type.getArgumentTypes()) addType(argument);
				addType(type.getReturnType());
			}
			default -> {
			}
		}
	}

	private void addField(String owner, String name, String descriptor, boolean isStatic) {
		addType(Type.getType(descriptor));
		if (owner.startsWith("[")) return;

		references.add(new LinkageReference(Kind.FIELD, owner, name + ":" + descriptor,
				EnumSet.of(isStatic ? Requirement.STATIC : Requirement.INSTANCE)));
	}

	private void addMethod(String owner, String name, String descriptor, boolean isStatic, boolean ownerIsInterface) {
		addType(Type.getMethodType(descriptor));
		if (owner.startsWith("[")) return;

		references.add(new LinkageReference(Kind.METHOD, owner, name + descriptor, EnumSet.of(
				isStatic ? Requirement.STATIC : Requirement.INSTANCE,
				ownerIsInterface ? Requirement.INTERFACE : Requirement.CLASS
		)));
	}

	private void addConstant(Object value) {
		if (value instanceof Type type) {
			addType(type);
			return;
		}
		if (value instanceof Handle handle) {
			addHandle(handle);
			return;
		}
		if (!(value instanceof ConstantDynamic constant)) return;

		addType(Type.getType(constant.getDescriptor()));
		addHandle(constant.getBootstrapMethod());
		for (int index = 0; index < constant.getBootstrapMethodArgumentCount(); index++)
			addConstant(constant.getBootstrapMethodArgument(index));
	}

	private void addHandle(Handle handle) {
		switch (handle.getTag()) {
			case Opcodes.H_GETSTATIC, Opcodes.H_PUTSTATIC -> addField(handle.getOwner(), handle.getName(), handle.getDesc(), true);
			case Opcodes.H_GETFIELD, Opcodes.H_PUTFIELD -> addField(handle.getOwner(), handle.getName(), handle.getDesc(), false);
			case Opcodes.H_INVOKESTATIC -> addMethod(handle.getOwner(), handle.getName(), handle.getDesc(), true, handle.isInterface());
			default -> addMethod(handle.getOwner(), handle.getName(), handle.getDesc(), false, handle.isInterface());
		}
	}

	/**
	 * Collects the references of a class's header and members.
	 */
	private final class ClassReferences extends ClassVisitor {
		private ClassReferences() {
			super(Opcodes.ASM9);
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			if (superName != null) addClass(superName, Requirement.CLASS);
			if (interfaces == null) return;

			for (String type : interfaces) addClass(type, Requirement.INTERFACE);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			addType(Type.getType(descriptor));
			return null;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			addType(Type.getMethodType(descriptor));
			if (exceptions != null)
				for (String exception : exceptions) addClass(exception);
			return new MethodReferences();
		}
	}

	/**
	 * Collects the references of a method's instructions.
	 */
	private final class MethodReferences extends MethodVisitor {
		private MethodReferences() {
			super(Opcodes.ASM9);
		}

		@Override
		public void visitTypeInsn(int opcode, String type) {
			if (opcode == Opcodes.NEW) {
				addClass(type, Requirement.CLASS);
				return;
			}

			addType(Type.getObjectType(type));
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
			addField(owner, name, descriptor, opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			addMethod(owner, name, descriptor, opcode == Opcodes.INVOKESTATIC, isInterface);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			addType(Type.getMethodType(descriptor));
			addHandle(bootstrapMethodHandle);
			for (Object argument : bootstrapMethodArguments) addConstant(argument);
		}

		@Override
		public void visitLdcInsn(Object value) {
			addConstant(value);
		}

		@Override
		public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
			addType(Type.getType(descriptor));
		}

		@Override
		public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
			if (type != null) addClass(type);
		}
	}
}
