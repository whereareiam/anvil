package me.whereareiam.anvil.protocol.mcprotocol.segment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The references of one segment's {@value SegmentDescriptor#LINKAGE}, verified against the classes a worker
 * actually loaded.
 *
 * <p>Each line names a class ({@code class a.b.C}), a field ({@code field a.b.C name:Ldescriptor;}) or a method
 * ({@code method a.b.C name(parameters)return}) with binary class names and JVM descriptors, followed by what the
 * build recorded the segment's code to require of it, as keywords in any order: {@code public} or
 * {@code protected} access, a {@code static} or {@code instance} member, and an owner that is a {@code class} or an
 * {@code interface}:</p>
 *
 * <pre>{@code
 * class a.b.C public class
 * field a.b.C NAME:Ljava/lang/String; public static
 * method a.b.Listener onEvent()V public instance interface
 * }</pre>
 *
 * <p>Verification looks each reference up directly, without scanning: the class through
 * {@link Class#forName(String, boolean, ClassLoader)} without initializing it, and a member through the JVM's own
 * member resolution, which also finds inherited members. Each requirement is then enforced the way the JVM links
 * the reference, so a release that turned a class into an interface, an instance member into a static one or a
 * public member into a narrower one fails here instead of with an {@link IncompatibleClassChangeError} or
 * {@link IllegalAccessError} later.</p>
 */
public final class SegmentLinkage {
	private static final String CONSTRUCTOR = "<init>";

	private final List<Reference> references;

	private SegmentLinkage(@NotNull List<Reference> references) {
		this.references = references;
	}

	/**
	 * Reads a linkage manifest.
	 *
	 * @param input content of {@value SegmentDescriptor#LINKAGE}
	 * @param source location used in failure messages
	 * @return the manifest's references in file order
	 * @throws IllegalArgumentException when a line is not a reference or names an unknown requirement
	 */
	public static @NotNull SegmentLinkage read(@NotNull InputStream input, @NotNull String source) {
		List<Reference> references = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) continue;

				references.add(Reference.parse(line.trim(), source));
			}
		} catch (IOException exception) {
			throw new IllegalArgumentException("Could not read the linkage manifest " + source, exception);
		}

		return new SegmentLinkage(List.copyOf(references));
	}

	/**
	 * Finds the first reference that does not link through a class loader.
	 *
	 * @param loader loader of the worker that runs the segment
	 * @return why the first failing reference does not link, such as {@code missing method a.b.C m()V} or
	 * {@code a.b.C is an interface, linked as a class}; empty when every reference links
	 */
	public @NotNull Optional<String> firstFailure(@NotNull ClassLoader loader) {
		for (Reference reference : references) {
			String failure = reference.failure(loader);
			if (failure != null) return Optional.of(failure);
		}

		return Optional.empty();
	}

	private enum Kind {
		CLASS, FIELD, METHOD;

		private String keyword() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private enum Requirement {
		PUBLIC, PROTECTED, STATIC, INSTANCE, CLASS, INTERFACE;

		private static @Nullable Requirement of(String keyword) {
			return Arrays.stream(values()).filter(requirement -> requirement.name().toLowerCase(Locale.ROOT).equals(keyword)).findFirst().orElse(null);
		}
	}

	/**
	 * One manifest line: the kind, the owner's binary name, the member (empty for a class) and its requirements.
	 */
	private record Reference(Kind kind, String owner, String member, Set<Requirement> requirements) {
		private static Reference parse(String line, String source) {
			String[] parts = line.split(" ");
			Kind kind = Arrays.stream(Kind.values()).filter(candidate -> candidate.keyword().equals(parts[0])).findFirst().orElse(null);
			int memberParts = kind == Kind.CLASS ? 0 : 1;
			if (kind == null || parts.length < 2 + memberParts) throw invalid(line, source);

			String member = memberParts == 1 ? parts[2] : "";
			if (kind == Kind.FIELD && member.indexOf(':') <= 0) throw invalid(line, source);
			if (kind == Kind.METHOD && member.indexOf('(') <= 0) throw invalid(line, source);

			Set<Requirement> requirements = EnumSet.noneOf(Requirement.class);
			for (String keyword : Arrays.asList(parts).subList(2 + memberParts, parts.length)) {
				Requirement requirement = Requirement.of(keyword);
				if (requirement == null)
					throw new IllegalArgumentException("'" + line + "' in " + source + " has an unknown requirement '" + keyword + "'");

				requirements.add(requirement);
			}
			return new Reference(kind, parts[1], member, requirements);
		}

		private static IllegalArgumentException invalid(String line, String source) {
			return new IllegalArgumentException("'" + line + "' in " + source + " is not a linkage reference");
		}

		private @Nullable String failure(ClassLoader loader) {
			Class<?> type;
			try {
				type = Class.forName(owner, false, loader);
			} catch (ClassNotFoundException | LinkageError exception) {
				return "missing class " + owner;
			}
			if (requirements.contains(Requirement.CLASS) && type.isInterface()) return owner + " is an interface, linked as a class";
			if (requirements.contains(Requirement.INTERFACE) && !type.isInterface()) return owner + " is a class, linked as an interface";
			if (kind == Kind.CLASS) return classAccess(type);

			String described = kind.keyword() + " " + owner + " " + member;
			Resolution resolution;
			try {
				resolution = resolve(type, loader);
			} catch (TypeNotPresentException | LinkageError | IllegalAccessException exception) {
				return "missing " + described;
			}
			if (resolution == null) return "missing " + described;

			if (requirements.contains(Requirement.STATIC) && !resolution.isStatic()) return described + " is not static, linked as static";
			if (requirements.contains(Requirement.INSTANCE) && resolution.isStatic()) return described + " is static, linked as instance";
			if (requirements.contains(Requirement.PUBLIC) && !Modifier.isPublic(resolution.modifiers())) return described + " is no longer public";
			if (requirements.contains(Requirement.PROTECTED) && !Modifier.isPublic(resolution.modifiers())
					&& !Modifier.isProtected(resolution.modifiers())) return described + " is no longer protected";
			return null;
		}

		private @Nullable String classAccess(Class<?> type) {
			if (!requirements.contains(Requirement.PUBLIC)) return null;

			try {
				// The lookup checks the class file's own access flags, as linking does, not those of a member class.
				MethodHandles.publicLookup().accessClass(type);
				return null;
			} catch (IllegalAccessException exception) {
				return "class " + owner + " is no longer public";
			}
		}

		private @Nullable Resolution resolve(Class<?> type, ClassLoader loader) throws IllegalAccessException {
			MethodHandles.Lookup lookup = type.getModule().isNamed()
					? MethodHandles.publicLookup()
					: MethodHandles.privateLookupIn(type, MethodHandles.lookup());
			if (kind == Kind.FIELD) {
				int separator = member.indexOf(':');
				String name = member.substring(0, separator);
				Class<?> fieldType = MethodType.fromMethodDescriptorString("()" + member.substring(separator + 1), loader).returnType();
				return found(lookup, () -> lookup.findGetter(type, name, fieldType), () -> lookup.findStaticGetter(type, name, fieldType));
			}

			int separator = member.indexOf('(');
			String name = member.substring(0, separator);
			MethodType methodType = MethodType.fromMethodDescriptorString(member.substring(separator), loader);
			if (name.equals(CONSTRUCTOR)) return found(lookup, () -> lookup.findConstructor(type, methodType), null);

			return found(lookup, () -> lookup.findVirtual(type, name, methodType), () -> lookup.findStatic(type, name, methodType));
		}

		private static @Nullable Resolution found(MethodHandles.Lookup lookup, MemberLookup instance, @Nullable MemberLookup statics) {
			MethodHandle handle = find(instance);
			if (handle != null) return new Resolution(false, lookup.revealDirect(handle).getModifiers());
			if (statics == null) return null;

			handle = find(statics);
			if (handle != null) return new Resolution(true, lookup.revealDirect(handle).getModifiers());
			return null;
		}

		private static @Nullable MethodHandle find(MemberLookup lookup) {
			try {
				return lookup.find();
			} catch (ReflectiveOperationException exception) {
				return null;
			}
		}
	}

	/**
	 * How a member resolved: whether it is static, and its modifiers.
	 */
	private record Resolution(boolean isStatic, int modifiers) {
	}

	@FunctionalInterface
	private interface MemberLookup {
		MethodHandle find() throws ReflectiveOperationException;
	}
}
