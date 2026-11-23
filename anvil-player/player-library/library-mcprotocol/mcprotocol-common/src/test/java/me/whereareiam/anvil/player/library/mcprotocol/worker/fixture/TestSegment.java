package me.whereareiam.anvil.player.library.mcprotocol.worker.fixture;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * A class path root that tests put on a worker's class path as a segment, or as a plain root, with adapters compiled
 * into it, so each adapter's class comes from the same root as the service file that declares it, as in a segment JAR.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestSegment {
	private final Path root;

	/**
	 * Creates a segment root with its descriptor and linkage manifest.
	 *
	 * @param root directory to create
	 * @param library library the segment belongs to
	 * @param owner owner the segment serves
	 * @param since first Minecraft version the segment serves
	 * @param linkage content of the linkage manifest
	 * @return the segment
	 */
	public static @NotNull TestSegment create(
			@NotNull Path root,
			@NotNull String library,
			@NotNull String owner,
			@NotNull String since,
			@NotNull String linkage
	) {
		try {
			Files.createDirectories(root.resolve("META-INF/anvil/segment"));
			Files.writeString(root.resolve("META-INF/anvil/segment.properties"),
					"library=" + library + "\nowner=" + owner + "\nsince=" + since + "\n");
			Files.writeString(root.resolve("META-INF/anvil/segment/linkage.txt"), linkage);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}

		return new TestSegment(root);
	}

	/**
	 * Creates a class path root that is no segment.
	 *
	 * @param root directory to create
	 * @return the root
	 */
	public static @NotNull TestSegment plain(@NotNull Path root) {
		try {
			Files.createDirectories(root);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}

		return new TestSegment(root);
	}

	/**
	 * Compiles a class into this root against the test class path and declares it a provider of a port.
	 *
	 * @param port port whose service file declares the class
	 * @param name binary name of the class
	 * @param source complete source of the class
	 * @return this root
	 */
	public @NotNull TestSegment provide(@NotNull Class<?> port, @NotNull String name, @NotNull String source) {
		try {
			Path sources = root.resolveSibling(root.getFileName() + "-sources");
			Path file = sources.resolve(name.replace('.', '/') + ".java");
			Files.createDirectories(file.getParent());
			Files.writeString(file, source);
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			int result = compiler.run(null, null, null,
					"-d", root.toString(), "-cp", System.getProperty("java.class.path"), file.toString());
			if (result != 0) throw new IllegalStateException("The source of " + name + " does not compile");

			Path services = root.resolve("META-INF/services").resolve(port.getName());
			Files.createDirectories(services.getParent());
			Files.writeString(services, name + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}

		return this;
	}

	/**
	 * Compiles a probe adapter that answers its name into this root.
	 *
	 * @param name binary name of the adapter
	 * @return this root
	 */
	public @NotNull TestSegment probe(@NotNull String name) {
		int split = name.lastIndexOf('.');
		return provide(ProbePackets.class, name, "package " + name.substring(0, split) + ";\n"
				+ "public final class " + name.substring(split + 1) + " implements " + ProbePackets.class.getName() + " {\n"
				+ "\tpublic String name() { return \"probe\"; }\n"
				+ "}\n");
	}

	/**
	 * Returns the root directory.
	 *
	 * @return root directory
	 */
	public @NotNull Path root() {
		return root;
	}
}
