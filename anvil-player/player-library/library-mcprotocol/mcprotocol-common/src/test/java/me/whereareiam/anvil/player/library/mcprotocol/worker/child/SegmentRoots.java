package me.whereareiam.anvil.player.library.mcprotocol.worker.child;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.ClassPath;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.JdkClasses;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A worker class path of chosen class-path roots: a loader that sees only those roots' resources, so a test controls
 * which segments a worker finds while classes still come from the test class path, where the real client segments
 * would otherwise be visible, and the class files that loader defines classes from, the test class path followed by
 * the roots, on which the worker checks each segment's linkage.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class SegmentRoots implements AutoCloseable {
	private final URLClassLoader loader;
	private final ClassPath classPath;

	/**
	 * Opens a worker class path of the given roots.
	 *
	 * @param roots segment directories and other class-path roots
	 * @return the class path, to close after use
	 */
	static @NotNull SegmentRoots open(@NotNull Path... roots) {
		URL[] urls = new URL[roots.length];
		List<Path> files = new ArrayList<>();
		for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator)))
			if (!entry.isEmpty()) files.add(Path.of(entry));
		try {
			for (int index = 0; index < roots.length; index++) {
				urls[index] = roots[index].toUri().toURL();
				files.add(roots[index]);
			}
		} catch (MalformedURLException exception) {
			throw new IllegalArgumentException(exception);
		}

		ClassLoader classesOnly = new ClassLoader(SegmentRoots.class.getClassLoader()) {
			@Override
			public URL getResource(String name) {
				return null;
			}

			@Override
			public Enumeration<URL> getResources(String name) {
				return Collections.emptyEnumeration();
			}
		};
		return new SegmentRoots(new URLClassLoader(urls, classesOnly), ClassPath.open(files, JdkClasses.running()));
	}

	/**
	 * Verifies the segments of a class path that holds none, for workers whose tests use no adapter.
	 *
	 * @return segments that provide no adapter
	 */
	static @NotNull WorkerSegments none() {
		try (SegmentRoots roots = open()) {
			return roots.verify(MinecraftVersion.parse("1.21.11"), warning -> {
				throw new AssertionError(warning);
			});
		}
	}

	/**
	 * Returns the loader that defines the worker's classes.
	 *
	 * @return loader of the roots
	 */
	@NotNull URLClassLoader loader() {
		return loader;
	}

	/**
	 * Verifies the segments of this class path as a worker does.
	 *
	 * @param version key version of the loaded release
	 * @param warnings receiver of one line per segment that does not link
	 * @return verified segments
	 */
	@NotNull WorkerSegments verify(@NotNull MinecraftVersion version, @NotNull Consumer<String> warnings) {
		return WorkerSegments.verify(loader, classPath, version, warnings);
	}

	@Override
	public void close() {
		try (loader) {
			classPath.close();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
