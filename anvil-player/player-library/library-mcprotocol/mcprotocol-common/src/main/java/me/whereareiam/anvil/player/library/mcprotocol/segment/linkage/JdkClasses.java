package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Classes of one JDK installation, read as class files from its module image through the {@code jrt:} file system.
 * Linkage manifests leave them out, and resolution reads them so members inherited from JDK types still resolve.
 *
 * <p>The build opens the JDK of a project's toolchain ({@link #open(Path)}), so a manifest and its check do not
 * depend on the JDK that runs Gradle; a worker reads the JDK it runs on ({@link #running()}). Close the instance after
 * use.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class JdkClasses implements AutoCloseable {
	private static final URI IMAGE = URI.create("jrt:/");

	private final FileSystem image;
	private final boolean owned;
	private final String origin;
	private final Map<String, Optional<ClassHeader>> headers = new HashMap<>();

	/**
	 * Opens the module image of the JDK installed at a Java home.
	 *
	 * @param javaHome installation directory of a JDK 9 or newer
	 * @return its classes, to close after use
	 * @throws UncheckedIOException when the installation has no module image
	 */
	public static @NotNull JdkClasses open(@NotNull Path javaHome) {
		try {
			FileSystem image = FileSystems.newFileSystem(IMAGE, Map.of("java.home", javaHome.toAbsolutePath().toString()));
			return new JdkClasses(image, true, "JDK " + javaHome.toAbsolutePath());
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not open the module image of the JDK at " + javaHome, exception);
		}
	}

	/**
	 * Reads the module image of the JDK the current JVM runs on.
	 *
	 * @return its classes; closing them leaves the JVM's own image open
	 */
	public static @NotNull JdkClasses running() {
		return new JdkClasses(FileSystems.getFileSystem(IMAGE), false, "JDK " + System.getProperty("java.home"));
	}

	/**
	 * Returns whether the JDK provides a class.
	 *
	 * @param className internal name of the class
	 * @return whether one of its modules contains it
	 */
	public boolean contains(@NotNull String className) {
		return header(className) != null;
	}

	/**
	 * Returns the header of a JDK class.
	 *
	 * @param className internal name of the class
	 * @return its header, or {@code null} when the JDK does not provide it
	 */
	@Nullable ClassHeader header(@NotNull String className) {
		return headers.computeIfAbsent(className, name -> Optional.ofNullable(read(name))).orElse(null);
	}

	@Override
	public void close() throws IOException {
		if (owned) image.close();
	}

	private @Nullable ClassHeader read(String className) {
		int separator = className.lastIndexOf('/');
		if (separator < 0) return null;

		Path packageLinks = image.getPath("/packages", className.substring(0, separator).replace('/', '.'));
		if (!Files.isDirectory(packageLinks)) return null;

		try (Stream<Path> modules = Files.list(packageLinks)) {
			Path classFile = modules.map(module -> image.getPath("/modules", module.getFileName().toString(), className + ".class"))
					.filter(Files::isRegularFile)
					.findFirst()
					.orElse(null);
			if (classFile == null) return null;

			return ClassHeader.read(ClassFiles.reader(Files.readAllBytes(classFile), origin + " class " + className));
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read " + className + " from the " + origin, exception);
		}
	}
}
