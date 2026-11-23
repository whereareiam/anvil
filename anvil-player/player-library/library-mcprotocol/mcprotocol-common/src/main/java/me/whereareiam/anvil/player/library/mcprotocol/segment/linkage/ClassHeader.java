package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The linkable shape of one class: its access flags, its supertypes, and the fields and methods it declares with
 * their access flags.
 *
 * <p>Supertypes are internal names, the superclass first, then the interfaces. Fields are keyed by
 * {@code name:descriptor}, methods by {@code name(parameters)return}.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class ClassHeader {
	private final int access;
	private final List<String> supertypes;
	private final Map<String, Integer> fields;
	private final Map<String, Integer> methods;

	/**
	 * Reads the header of a class file.
	 *
	 * @param reader reader of the class file
	 * @return its header
	 */
	static @NotNull ClassHeader read(@NotNull ClassReader reader) {
		Map<String, Integer> fields = new HashMap<>();
		Map<String, Integer> methods = new HashMap<>();
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				fields.put(name + ":" + descriptor, access);
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				methods.put(name + descriptor, access);
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

		List<String> supertypes = new ArrayList<>();
		if (reader.getSuperName() != null) supertypes.add(reader.getSuperName());
		supertypes.addAll(Arrays.asList(reader.getInterfaces()));
		return new ClassHeader(reader.getAccess(), List.copyOf(supertypes), Map.copyOf(fields), Map.copyOf(methods));
	}

	/**
	 * Returns whether the class is an interface.
	 *
	 * @return whether its access flags carry {@code ACC_INTERFACE}
	 */
	boolean isInterface() {
		return (access & Opcodes.ACC_INTERFACE) != 0;
	}

	/**
	 * Returns the supertypes, the superclass first.
	 *
	 * @return internal names
	 */
	@NotNull List<String> supertypes() {
		return supertypes;
	}

	/**
	 * Returns the access flags of what a reference names when this class declares it: the class's own flags for a
	 * class reference, otherwise the member's flags.
	 *
	 * @param reference the reference
	 * @return the flags, or {@code null} when the class does not declare the member
	 */
	@Nullable Integer accessOf(@NotNull LinkageReference reference) {
		return switch (reference.kind()) {
			case CLASS -> access;
			case FIELD -> fields.get(reference.member());
			case METHOD -> methods.get(reference.member());
		};
	}

	/**
	 * Returns whether this class itself declares the member a reference names.
	 *
	 * @param reference the reference
	 * @return whether it is declared here
	 */
	boolean declares(@NotNull LinkageReference reference) {
		return accessOf(reference) != null;
	}
}
