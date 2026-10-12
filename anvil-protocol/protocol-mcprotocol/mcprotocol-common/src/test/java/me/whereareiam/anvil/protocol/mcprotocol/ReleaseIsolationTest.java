package me.whereareiam.anvil.protocol.mcprotocol;

import me.whereareiam.anvil.protocol.worker.child.ProtocolWorker;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This library's provider and worker client, and the native worker that hosts it, must not link against
 * MCProtocolLib or the libraries whose versions each release pins, because every release brings its own copy in its
 * closure.
 */
class ReleaseIsolationTest {
	@Test
	void mainClassesReferenceNoPackageTheClosureOwns() throws Exception {
		List<String> violations = new ArrayList<>();
		int scanned = 0;
		for (Class<?> owner : List.of(McProtocolWorkerLibrary.class, ProtocolWorker.class)) {
			Path location = Path.of(owner.getProtectionDomain().getCodeSource().getLocation().toURI());
			// Tests see this project's classes as a directory and the worker's as a directory or a JAR.
			try (FileSystem jar = Files.isDirectory(location) ? null : FileSystems.newFileSystem(location)) {
				Path classes = jar == null ? location : jar.getPath("/");
				try (Stream<Path> files = Files.walk(classes)) {
					for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
						scanned++;
						try (InputStream input = Files.newInputStream(file)) {
							for (String type : referencedTypes(input))
								if (McProtocolWorkerLibrary.LIBRARY.getOwnedPackages().stream().anyMatch(type::contains))
									violations.add(location.getFileName() + "/" + classes.relativize(file) + " -> " + type);
						}
					}
				}
			}
		}

		assertTrue(scanned > 20, "scanned only " + scanned + " classes");
		assertEquals(List.of(), violations);
	}

	/**
	 * Reads the class names and descriptors a class file's constant pool links against, leaving out text that
	 * is only used as a string constant.
	 */
	private Set<String> referencedTypes(InputStream stream) throws IOException {
		DataInputStream input = new DataInputStream(stream);
		input.skipNBytes(8);
		int count = input.readUnsignedShort();
		String[] texts = new String[count];
		Set<Integer> strings = new HashSet<>();
		for (int index = 1; index < count; index++) {
			int tag = input.readUnsignedByte();
			switch (tag) {
				case 1 -> texts[index] = input.readUTF();
				case 8 -> strings.add(input.readUnsignedShort());
				case 7, 16, 19, 20 -> input.skipNBytes(2);
				case 15 -> input.skipNBytes(3);
				case 3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
				case 5, 6 -> {
					input.skipNBytes(8);
					index++;
				}
				default -> throw new IOException("Unknown constant pool tag " + tag);
			}
		}

		Set<String> types = new HashSet<>();
		for (int index = 1; index < count; index++)
			if (texts[index] != null && !strings.contains(index)) types.add(texts[index]);
		return types;
	}
}
