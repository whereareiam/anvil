package me.whereareiam.anvil.tooling.gradle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans compiled class headers for Anvil scenario definitions without loading or initializing classes.
 * <p>
 * A definition is a public, concrete class that reaches {@code AnvilScenarioDefinition} through its
 * superclasses or interfaces, including abstract bases and sub-interfaces in the scanned classes.
 * Supertypes outside the scanned directories are not inspected.
 */
public final class ScenarioDefinitionScanner {
	private static final int CLASS_MAGIC = 0xCAFEBABE;
	private static final int ACC_PUBLIC = 0x0001;
	private static final int ACC_INTERFACE = 0x0200;
	private static final int ACC_ABSTRACT = 0x0400;
	private static final int ACC_SYNTHETIC = 0x1000;
	private static final String DEFINITION_INTERFACE = "me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition";

	/**
	 * Returns definition class names from compiled classes and existing project index resources.
	 *
	 * @param classes compiled class directories to scan
	 * @param resources resource directories that may contain {@code META-INF/anvil/scenarios}
	 * @return sorted definition class names
	 * @throws IOException when a directory or class file cannot be read
	 */
	public static @NotNull List<String> scan(
			@NotNull Iterable<File> classes,
			@NotNull Iterable<File> resources
	) throws IOException {
		Map<String, TypeHeader> types = new HashMap<>();
		for (File entry : classes) {
			Path root = entry.toPath();
			if (!Files.isDirectory(root)) continue;

			List<Path> files;
			try (var paths = Files.walk(root)) {
				files = paths.filter(path -> path.toString().endsWith(".class")).toList();
			}
			for (Path file : files) {
				TypeHeader type = readClass(file);
				if (type != null) types.put(type.name(), type);
			}
		}

		Set<String> names = new LinkedHashSet<>(indexed(resources));
		for (TypeHeader type : types.values())
			if (type.instantiable() && reachesDefinition(type.name(), types, new HashSet<>())) names.add(type.name());

		return names.stream().sorted().toList();
	}

	/**
	 * Writes an index resource into a caller-owned directory for a prepared JVM classpath.
	 *
	 * @param output directory that receives {@code META-INF/anvil/scenarios}
	 * @param definitions definition class names
	 * @throws IOException when the index cannot be written
	 */
	public static void writeIndex(@NotNull Path output, @NotNull List<String> definitions) throws IOException {
		Path index = output.resolve("META-INF/anvil/scenarios");
		Files.createDirectories(index.getParent());
		String content = definitions.isEmpty() ? "" : String.join("\n", definitions) + "\n";
		Files.writeString(index, content, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
	}

	private static @NotNull List<String> indexed(@NotNull Iterable<File> resources) throws IOException {
		List<String> names = new ArrayList<>();
		for (File entry : resources) {
			Path index = entry.toPath().resolve("META-INF/anvil/scenarios");
			if (!Files.isRegularFile(index)) continue;

			for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
				String name = line.strip();
				if (!name.isEmpty() && !name.startsWith("#")) names.add(name);
			}
		}

		return names;
	}

	private static @Nullable TypeHeader readClass(@NotNull Path path) throws IOException {
		try (var input = new DataInputStream(Files.newInputStream(path))) {
			if (input.readInt() != CLASS_MAGIC) throw new IOException("Not a class file");

			input.readUnsignedShort();
			input.readUnsignedShort();
			Object[] pool = readConstantPool(input);
			int access = input.readUnsignedShort();
			String name = className(pool, input.readUnsignedShort());
			String superclass = className(pool, input.readUnsignedShort());
			if (name == null || name.equals("module-info") || name.endsWith("/package-info")) return null;

			List<String> supertypes = new ArrayList<>();
			if (superclass != null) supertypes.add(binaryName(superclass));
			for (int index = 0, count = input.readUnsignedShort(); index < count; index++) {
				String implemented = className(pool, input.readUnsignedShort());
				if (implemented != null) supertypes.add(binaryName(implemented));
			}

			return new TypeHeader(binaryName(name), access, List.copyOf(supertypes));
		} catch (IOException | RuntimeException failure) {
			throw new IOException("Could not read compiled class " + path, failure);
		}
	}

	private static @NotNull Object[] readConstantPool(@NotNull DataInputStream input) throws IOException {
		Object[] pool = new Object[input.readUnsignedShort()];
		for (int index = 1; index < pool.length; index++) {
			switch (input.readUnsignedByte()) {
				case 1 -> pool[index] = input.readUTF();
				case 3, 4 -> input.skipBytes(4);
				case 5, 6 -> {
					input.skipBytes(8);
					index++;
				}
				case 7, 8, 16, 19, 20 -> pool[index] = input.readUnsignedShort();
				case 9, 10, 11, 12, 17, 18 -> input.skipBytes(4);
				case 15 -> input.skipBytes(3);
				default -> throw new IOException("Unknown class-file constant-pool tag");
			}
		}

		return pool;
	}

	private static @Nullable String className(@NotNull Object[] pool, int index) {
		if (index <= 0 || index >= pool.length || !(pool[index] instanceof Integer nameIndex)) return null;
		if (nameIndex <= 0 || nameIndex >= pool.length || !(pool[nameIndex] instanceof String name)) return null;

		return name;
	}

	private static @NotNull String binaryName(@NotNull String internalName) {
		return internalName.replace('/', '.');
	}

	private static boolean reachesDefinition(
			@NotNull String name,
			@NotNull Map<String, TypeHeader> types,
			@NotNull Set<String> visited
	) {
		if (!visited.add(name)) return false;

		TypeHeader type = types.get(name);
		if (type == null) return false;

		for (String supertype : type.supertypes())
			if (supertype.equals(DEFINITION_INTERFACE) || reachesDefinition(supertype, types, visited)) return true;

		return false;
	}

	/**
	 * One scanned class: its name, access flags, and direct superclass and interfaces.
	 */
	private record TypeHeader(String name, int access, List<String> supertypes) {
		/**
		 * Whether the runner can instantiate this class reflectively as a definition.
		 */
		boolean instantiable() {
			return (access & ACC_PUBLIC) != 0 && (access & (ACC_INTERFACE | ACC_ABSTRACT | ACC_SYNTHETIC)) == 0;
		}
	}
}
