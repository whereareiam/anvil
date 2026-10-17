package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Class loaders that see only chosen class-path roots' resources, so a test controls which segments a worker finds
 * while classes still come from the test class path, where the real client segments would otherwise be visible.
 */
final class SegmentRoots {
	/**
	 * Creates a loader whose resources come only from the given roots.
	 *
	 * @param roots segment directories and other class-path roots
	 * @return loader to close after use
	 */
	static @NotNull URLClassLoader loader(@NotNull Path... roots) {
		URL[] urls = new URL[roots.length];
		try {
			for (int index = 0; index < roots.length; index++) urls[index] = roots[index].toUri().toURL();
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
		return new URLClassLoader(urls, classesOnly);
	}

	/**
	 * Verifies the segments of a class path that holds none, for workers whose tests use no adapter.
	 *
	 * @return segments that provide no adapter
	 */
	static @NotNull WorkerSegments none() {
		return WorkerSegments.verify(loader(), MinecraftVersion.parse("1.21.11"), warning -> {
			throw new AssertionError(warning);
		});
	}
}
