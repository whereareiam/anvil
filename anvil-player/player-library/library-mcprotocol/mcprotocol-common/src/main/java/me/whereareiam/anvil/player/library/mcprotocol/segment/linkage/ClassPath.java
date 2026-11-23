package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference.Kind;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference.Requirement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The classes of one class path on top of a JDK, read as class files, resolving linkage references the way the JVM
 * links them: a member may be declared by the named class or inherited from its superclasses and interfaces, JDK
 * supertypes included; a constructor only by the named class itself.
 *
 * <p>Opening a class path lists its class files without reading them; each class is read once, when a resolution
 * first needs it. Like a class loader, the first root that holds a class wins. JAR files and class directories are
 * roots; other files and missing paths hold no classes. Close the instance after use; the JDK stays open.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClassPath implements AutoCloseable {
	private static final String CLASS_SUFFIX = ".class";
	private static final String MODULE_INFO = "module-info.class";

	private final Map<String, ClassFile> files;
	private final List<ZipFile> jars;
	private final JdkClasses jdk;
	private final Map<String, Optional<ClassHeader>> headers = new HashMap<>();

	/**
	 * Opens the classes of class path roots on top of a JDK.
	 *
	 * @param roots JAR files and class directories in class path order
	 * @param jdk the JDK the class path runs on
	 * @return the class path, to close after use
	 * @throws UncheckedIOException when a root cannot be listed
	 */
	public static @NotNull ClassPath open(@NotNull Collection<Path> roots, @NotNull JdkClasses jdk) {
		Map<String, ClassFile> files = new LinkedHashMap<>();
		List<ZipFile> jars = new ArrayList<>();
		try {
			for (Path root : roots) {
				if (Files.isDirectory(root)) {
					listDirectory(root, files);
					continue;
				}
				if (!Files.isRegularFile(root) || !root.getFileName().toString().endsWith(".jar")) continue;

				ZipFile jar = new ZipFile(root.toFile());
				jars.add(jar);
				jar.stream()
						.filter(entry -> !entry.isDirectory() && entry.getName().endsWith(CLASS_SUFFIX))
						.filter(entry -> !entry.getName().startsWith("META-INF/") && !entry.getName().endsWith(MODULE_INFO))
						.forEach(entry -> files.putIfAbsent(className(entry.getName()), new JarClassFile(root, jar, entry)));
			}
		} catch (IOException exception) {
			UncheckedIOException failure = new UncheckedIOException("Could not list the class path " + roots, exception);
			closeAll(jars, failure);
			throw failure;
		} catch (UncheckedIOException failure) {
			closeAll(jars, failure);
			throw failure;
		}

		return new ClassPath(files, jars, jdk);
	}

	/**
	 * Opens the class path the current JVM runs on: the entries of its {@code java.class.path} on top of its own JDK.
	 *
	 * @return the class path, to close after use
	 */
	public static @NotNull ClassPath running() {
		List<Path> roots = new ArrayList<>();
		for (String entry : System.getProperty("java.class.path", "").split(Pattern.quote(File.pathSeparator)))
			if (!entry.isBlank()) roots.add(Path.of(entry));
		return open(roots, JdkClasses.running());
	}

	/**
	 * Returns the JDK the class path runs on.
	 *
	 * @return its JDK classes
	 */
	public @NotNull JdkClasses jdk() {
		return jdk;
	}

	/**
	 * Returns whether the class path itself, without the JDK, holds a class.
	 *
	 * @param className internal name of the class
	 * @return whether a root holds its class file
	 */
	public boolean contains(@NotNull String className) {
		return files.containsKey(className);
	}

	/**
	 * Returns the internal names of the class path's classes, without the JDK.
	 *
	 * @return unmodifiable names
	 */
	public @NotNull Set<String> classNames() {
		return Collections.unmodifiableSet(files.keySet());
	}

	/**
	 * Describes why a reference does not link on this class path: a missing class or member, a class that changed
	 * between class and interface, a member that changed between static and instance, or a narrower access than the
	 * reference requires.
	 *
	 * @param reference the reference
	 * @return the failure, such as {@code missing method a.b.C m()V} or {@code a.b.C is an interface, linked as a
	 * class}; {@code null} when the reference links
	 * @throws IllegalArgumentException when a class file it reads cannot be read
	 */
	public @Nullable String linkageFailure(@NotNull LinkageReference reference) {
		String owner = reference.binaryOwner();
		ClassHeader header = header(reference.owner());
		if (header == null) return "missing class " + owner;

		Set<Requirement> requirements = reference.requirements();
		if (requirements.contains(Requirement.CLASS) && header.isInterface()) return owner + " is an interface, linked as a class";
		if (requirements.contains(Requirement.INTERFACE) && !header.isInterface()) return owner + " is a class, linked as an interface";

		String described = reference.kind() == Kind.CLASS
				? "class " + owner
				: reference.kind().keyword() + " " + owner + " " + reference.member();
		Integer access = declaredAccess(reference);
		if (access == null) return "missing " + described;

		boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
		if (requirements.contains(Requirement.STATIC) && !isStatic) return described + " is not static, linked as static";
		if (requirements.contains(Requirement.INSTANCE) && isStatic) return described + " is static, linked as instance";
		if (requirements.contains(Requirement.PUBLIC) && (access & Opcodes.ACC_PUBLIC) == 0) return described + " is no longer public";
		if (requirements.contains(Requirement.PROTECTED) && (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0)
			return described + " is no longer protected";
		return null;
	}

	/**
	 * Returns the least access the class path grants to what a reference names.
	 *
	 * @param reference the reference
	 * @return {@link Requirement#PUBLIC}, {@link Requirement#PROTECTED}, or {@code null} when it is neither or does
	 * not resolve
	 */
	public @Nullable Requirement accessOf(@NotNull LinkageReference reference) {
		Integer access = declaredAccess(reference);
		if (access == null) return null;
		if ((access & Opcodes.ACC_PUBLIC) != 0) return Requirement.PUBLIC;
		if ((access & Opcodes.ACC_PROTECTED) != 0) return Requirement.PROTECTED;
		return null;
	}

	/**
	 * Finds the class that declares the member a reference names, starting at its owner and searching the
	 * superclasses before the interfaces. A class reference is declared by its owner, and so is a constructor or class
	 * initializer, which no class inherits.
	 *
	 * @param reference the reference
	 * @return internal name of the declaring class, or {@code null} when the member does not resolve
	 */
	public @Nullable String declaringClass(@NotNull LinkageReference reference) {
		if (isInitializer(reference)) {
			ClassHeader header = header(reference.owner());
			return header != null && header.declares(reference) ? reference.owner() : null;
		}

		return declaringClass(reference, reference.owner(), new HashSet<>());
	}

	@Override
	public void close() throws IOException {
		IOException failure = null;
		for (ZipFile jar : jars) {
			try {
				jar.close();
			} catch (IOException exception) {
				if (failure == null) failure = exception;
				else failure.addSuppressed(exception);
			}
		}
		if (failure != null) throw failure;
	}

	private @Nullable Integer declaredAccess(LinkageReference reference) {
		String declaring = declaringClass(reference);
		if (declaring == null) return null;

		ClassHeader header = header(declaring);
		return header == null ? null : header.accessOf(reference);
	}

	private @Nullable String declaringClass(LinkageReference reference, String type, Set<String> visited) {
		if (!visited.add(type)) return null;

		ClassHeader header = header(type);
		if (header == null) return null;
		if (header.declares(reference)) return type;

		for (String supertype : header.supertypes()) {
			String declaring = declaringClass(reference, supertype, visited);
			if (declaring != null) return declaring;
		}
		return null;
	}

	/**
	 * Returns the header of a class of the class path, else of the JDK.
	 */
	@Nullable ClassHeader header(@NotNull String className) {
		ClassFile file = files.get(className);
		if (file == null) return jdk.header(className);

		return headers.computeIfAbsent(className, name -> Optional.of(ClassHeader.read(ClassFiles.reader(file.read(), file.origin()))))
				.orElse(null);
	}

	private static boolean isInitializer(LinkageReference reference) {
		return reference.kind() == Kind.METHOD && (reference.member().startsWith("<init>(") || reference.member().startsWith("<clinit>("));
	}

	private static void listDirectory(Path root, Map<String, ClassFile> files) throws IOException {
		try (Stream<Path> found = Files.walk(root)) {
			found.filter(Files::isRegularFile)
					.filter(file -> file.getFileName().toString().endsWith(CLASS_SUFFIX))
					.filter(file -> !file.getFileName().toString().equals(MODULE_INFO))
					.sorted()
					.forEach(file -> files.putIfAbsent(className(root.relativize(file).toString().replace(File.separatorChar, '/')),
							new DirectoryClassFile(file)));
		}
	}

	private static String className(String path) {
		return path.substring(0, path.length() - CLASS_SUFFIX.length());
	}

	private static void closeAll(List<ZipFile> jars, Exception failure) {
		for (ZipFile jar : jars) {
			try {
				jar.close();
			} catch (IOException exception) {
				failure.addSuppressed(exception);
			}
		}
	}

	/**
	 * Where a class file of the class path lies.
	 */
	private interface ClassFile {
		byte[] read();

		String origin();
	}

	private record DirectoryClassFile(Path file) implements ClassFile {
		@Override
		public byte[] read() {
			try {
				return Files.readAllBytes(file);
			} catch (IOException exception) {
				throw new UncheckedIOException("Could not read " + file, exception);
			}
		}

		@Override
		public String origin() {
			return file.toString();
		}
	}

	private record JarClassFile(Path root, ZipFile jar, ZipEntry entry) implements ClassFile {
		@Override
		public byte[] read() {
			try (InputStream input = jar.getInputStream(entry)) {
				return input.readAllBytes();
			} catch (IOException exception) {
				throw new UncheckedIOException("Could not read " + origin(), exception);
			}
		}

		@Override
		public String origin() {
			return root.getFileName() + "!/" + entry.getName();
		}
	}
}
