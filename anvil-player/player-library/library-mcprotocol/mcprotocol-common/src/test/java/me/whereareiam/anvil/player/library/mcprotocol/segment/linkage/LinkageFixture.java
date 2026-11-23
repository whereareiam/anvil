package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A tiny library compiled in two releases, a project-dependency port, and a segment compiled against the first release
 * that implements the port. The later release drops {@code Api.removed()}, makes {@code Api.count()} an instance
 * method, narrows {@code Api.narrowed()} to protected and turns the {@code Handler} interface into a class.
 *
 * <p>The fixture opens the JDK that runs the tests from its Java home; close it after use.</p>
 */
final class LinkageFixture implements AutoCloseable {
	private static final Pattern PACKAGE = Pattern.compile("package ([\\w.]+);");
	private static final Pattern TYPE = Pattern.compile("(?:class|interface) (\\w+)");

	private final Path directory;
	private final JdkClasses jdk;
	private final Path firstRelease;
	private final Path laterRelease;
	private final Path port;
	private final Path segment;

	LinkageFixture(@NotNull Path directory) {
		this.directory = directory;
		this.jdk = JdkClasses.open(Path.of(System.getProperty("java.home")));
		String base = "package lib; public class Base { public void inherited() {} protected void helper() {} }";
		String listener = "package lib; public interface Listener { default void onEvent() {} }";
		firstRelease = compile("first", List.of(), base, listener,
				"package lib; public interface Handler { void handle(); }",
				"""
				package lib;
				public class Api extends Base {
					public static final String NAME = "api";
					public static String version = "1";
					public void kept() {}
					public void removed() {}
					public static int count() { return 0; }
					public void narrowed() {}
				}
				""");
		laterRelease = jar(compile("later", List.of(), base, listener,
				"package lib; public abstract class Handler { public abstract void handle(); }",
				"""
				package lib;
				public class Api extends Base {
					public static String version = "2";
					public void kept() {}
					public int count() { return 0; }
					protected void narrowed() {}
				}
				"""));
		port = compile("port", List.of(firstRelease),
				"package demo.port; public interface Port { String describe(lib.Handler handler); }");
		segment = compile("segment", List.of(firstRelease, port), """
				package demo.lib.v1_0;
				import java.util.List;
				public final class Adapter extends lib.Base implements demo.port.Port, lib.Listener {
					@Override
					public String describe(lib.Handler handler) {
						lib.Api api = new lib.Api();
						api.kept();
						api.removed();
						api.inherited();
						api.narrowed();
						helper();
						onEvent();
						handler.handle();
						Runnable task = api::kept;
						task.run();
						return List.of(lib.Api.NAME, lib.Api.version, String.valueOf(lib.Api.count() + api.hashCode())).toString();
					}
				}
				""");
	}

	/**
	 * Returns the JDK that runs the tests.
	 */
	@NotNull JdkClasses jdk() {
		return jdk;
	}

	/**
	 * Returns the class directory of the release the segment compiled against.
	 */
	@NotNull Path firstRelease() {
		return firstRelease;
	}

	/**
	 * Returns the JAR of the later release.
	 */
	@NotNull Path laterRelease() {
		return laterRelease;
	}

	/**
	 * Opens a class path of roots on the fixture's JDK.
	 */
	@NotNull ClassPath classPath(@NotNull Path... roots) {
		return ClassPath.open(List.of(roots), jdk);
	}

	/**
	 * Returns what the segment links against outside the JDK, its own package and the port's package, as the segment
	 * manifest records it.
	 */
	@NotNull Set<LinkageReference> externalReferences() {
		LinkageCollector collector = new LinkageCollector();
		try (Stream<Path> files = Files.walk(segment);
		     ClassPath classpath = classPath(firstRelease, port);
		     ClassPath projectClasses = classPath(port)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList())
				collector.add(Files.readAllBytes(file), file.toString());
			return collector.segmentReferences(classpath, projectClasses);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Override
	public void close() throws IOException {
		jdk.close();
	}

	/**
	 * Compiles sources into a class directory against a class path.
	 *
	 * @param name name of the class directory
	 * @param classpath class path roots
	 * @param sources complete sources, one type each
	 * @return the class directory
	 */
	@NotNull Path compile(@NotNull String name, @NotNull List<Path> classpath, @NotNull String... sources) {
		try {
			Path sourceRoot = directory.resolve(name + "-sources");
			Path output = Files.createDirectories(directory.resolve(name));
			List<String> arguments = new ArrayList<>(List.of("-d", output.toString(), "--release", "21"));
			if (!classpath.isEmpty()) {
				arguments.add("-classpath");
				arguments.add(classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
			}
			for (String source : sources) {
				String text = source.stripIndent();
				Matcher packageName = PACKAGE.matcher(text);
				Matcher typeName = TYPE.matcher(text);
				if (!packageName.find() || !typeName.find()) throw new IllegalArgumentException("No type in " + text);

				Path file = sourceRoot.resolve(packageName.group(1).replace('.', '/')).resolve(typeName.group(1) + ".java");
				Files.createDirectories(file.getParent());
				Files.writeString(file, text);
				arguments.add(file.toString());
			}
			assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)),
					"javac failed for " + name);
			return output;
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private Path jar(Path classes) {
		Path jar = directory.resolve(classes.getFileName() + ".jar");
		try (OutputStream file = Files.newOutputStream(jar);
		     JarOutputStream output = new JarOutputStream(file);
		     Stream<Path> entries = Files.walk(classes)) {
			for (Path entry : entries.filter(Files::isRegularFile).sorted().toList()) {
				output.putNextEntry(new JarEntry(classes.relativize(entry).toString().replace('\\', '/')));
				Files.copy(entry, output);
				output.closeEntry();
			}
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		return jar;
	}
}
