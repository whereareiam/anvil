package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.segment.SegmentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Builds the explicit class path of one release's worker: the release's runtime closure first, then the
 * host's class path entries.
 *
 * <p>Host entries come from {@code java.class.path} and every {@link URLClassLoader} in the context loader
 * chain, as collected by {@link #hostEntries(ClassLoader)}. A host entry carrying
 * {@value SegmentDescriptor#PROPERTIES} is kept only when it is the selected segment of its owner: the
 * MCProtocolLib segment with the greatest start version that does not exceed the release key. Exactly one kept
 * entry must be a client segment, and no kept entry may contain classes of the release's own packages, which
 * come only from the closure.</p>
 */
final class WorkerClasspath {
	private static final List<String> RELEASE_PACKAGES = List.of(
			"io/netty/",
			"org/geysermc/mcprotocollib/",
			"com/github/steveice10/",
			"org/cloudburstmc/",
			"net/kyori/adventure/"
	);
	private static final String CLIENT_SERVICE = "META-INF/services/" + McProtocolClient.class.getName();

	/**
	 * Collects the host's class path entries: {@code java.class.path} followed by the file URLs of every
	 * {@link URLClassLoader} in a context loader chain.
	 *
	 * @param contextLoader loader whose URL class loader chain contributes entries, or null
	 * @return distinct absolute entries in class path order
	 * @throws IllegalArgumentException when a URL class loader holds a URL that is not a local file
	 */
	static @NotNull List<Path> hostEntries(@Nullable ClassLoader contextLoader) {
		Set<Path> paths = new LinkedHashSet<>();
		for (String entry : System.getProperty("java.class.path", "").split(Pattern.quote(File.pathSeparator)))
			if (!entry.isBlank()) paths.add(Path.of(entry).toAbsolutePath());
		for (ClassLoader loader = contextLoader; loader != null; loader = loader.getParent())
			if (loader instanceof URLClassLoader urls)
				for (URL url : urls.getURLs()) paths.add(path(url));

		return List.copyOf(paths);
	}

	/**
	 * Selects the worker class path for one release.
	 *
	 * @param release release the worker loads
	 * @param closure the release's runtime closure in classpath order
	 * @param hostEntries the host's class path entries in order
	 * @return closure followed by the kept host entries
	 * @throws IllegalStateException when not exactly one client segment is kept, two segments of one owner
	 * start at the same version, or a kept host entry contains classes of the release's packages
	 */
	@NotNull List<Path> resolve(@NotNull ProtocolRelease release, @NotNull List<Path> closure, @NotNull List<Path> hostEntries) {
		Set<Path> paths = new LinkedHashSet<>();
		hostEntries.forEach(entry -> paths.add(entry.toAbsolutePath()));
		closure.forEach(jar -> paths.remove(jar.toAbsolutePath()));

		List<Entry> entries = paths.stream().map(this::inspect).toList();
		Set<Path> selected = select(entries, release.version());
		List<Entry> kept = entries.stream().filter(entry -> entry.segment() == null || selected.contains(entry.path())).toList();

		List<Path> clients = kept.stream().filter(Entry::client).map(Entry::path).toList();
		if (clients.size() != 1 || kept.stream().anyMatch(entry -> entry.client() && entry.segment() == null))
			throw new IllegalStateException("MCProtocolLib release " + release.getLibraryVersion() + " needs exactly one client segment "
					+ "serving Minecraft " + release.version() + ", but the host class path provides " + clients);
		for (Entry entry : kept)
			if (!entry.releasePackages().isEmpty())
				throw new IllegalStateException("Worker class path entry " + entry.path() + " contains " + entry.releasePackages()
						+ " classes; they must come only from the runtime closure of MCProtocolLib release " + release.getLibraryVersion());

		List<Path> classpath = new ArrayList<>(closure.size() + kept.size());
		closure.forEach(jar -> classpath.add(jar.toAbsolutePath()));
		kept.forEach(entry -> classpath.add(entry.path()));
		return List.copyOf(classpath);
	}

	private Set<Path> select(List<Entry> entries, MinecraftVersion key) {
		Map<String, Map<MinecraftVersion, Path>> owners = new LinkedHashMap<>();
		for (Entry entry : entries) {
			SegmentDescriptor segment = entry.segment();
			if (segment == null || !segment.getLibrary().equals(McProtocolClient.LIBRARY_ID)) continue;

			Path previous = owners.computeIfAbsent(segment.getOwner(), ignored -> new LinkedHashMap<>())
					.putIfAbsent(segment.getSince(), entry.path());
			if (previous != null)
				throw new IllegalStateException("Segments " + previous + " and " + entry.path() + " of " + segment.getOwner()
						+ " both start at Minecraft " + segment.getSince());
		}

		Set<Path> selected = new LinkedHashSet<>();
		for (Map<MinecraftVersion, Path> starts : owners.values())
			MinecraftVersion.floor(starts.keySet(), since -> since, key).map(starts::get).ifPresent(selected::add);
		return selected;
	}

	private Entry inspect(Path path) {
		try {
			if (Files.isDirectory(path)) return directory(path);
			if (Files.isRegularFile(path)) return jar(path);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not inspect worker class path entry " + path, exception);
		}

		return new Entry(path, null, false, Set.of());
	}

	private Entry directory(Path path) throws IOException {
		SegmentDescriptor segment = null;
		Path properties = path.resolve(SegmentDescriptor.PROPERTIES);
		if (Files.isRegularFile(properties))
			try (InputStream input = Files.newInputStream(properties)) {
				segment = SegmentDescriptor.read(input, path.toString());
			}

		Set<String> packages = new LinkedHashSet<>();
		for (String name : RELEASE_PACKAGES)
			if (Files.isDirectory(path.resolve(name))) packages.add(name);
		return new Entry(path, segment, Files.isRegularFile(path.resolve(CLIENT_SERVICE)), packages);
	}

	private Entry jar(Path path) throws IOException {
		try (JarFile jar = new JarFile(path.toFile())) {
			SegmentDescriptor segment = null;
			JarEntry properties = jar.getJarEntry(SegmentDescriptor.PROPERTIES);
			if (properties != null)
				try (InputStream input = jar.getInputStream(properties)) {
					segment = SegmentDescriptor.read(input, path.toString());
				}

			Set<String> packages = new LinkedHashSet<>();
			jar.stream().map(JarEntry::getName).filter(name -> name.endsWith(".class")).forEach(name -> {
				for (String release : RELEASE_PACKAGES)
					if (name.startsWith(release)) packages.add(release);
			});
			return new Entry(path, segment, jar.getJarEntry(CLIENT_SERVICE) != null, packages);
		}
	}

	private static Path path(URL url) {
		if (!url.getProtocol().equals("file"))
			throw new IllegalArgumentException("Worker classpath requires local file URLs: " + url);
		try {
			return Path.of(url.toURI()).toAbsolutePath();
		} catch (URISyntaxException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid worker classpath URL: " + url, exception);
		}
	}

	/**
	 * What one host class path entry offers the worker.
	 */
	private record Entry(Path path, @Nullable SegmentDescriptor segment, boolean client, Set<String> releasePackages) {
	}
}
