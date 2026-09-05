package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Preserves host/extension dependencies while selecting one exact MCProtocolLib runtime.
 */
final class WorkerClasspathResolver {
	private static final List<String> CODEC_RESOURCES = List.of(
			"org/geysermc/mcprotocollib/protocol/codec/MinecraftCodec.class",
			"com/github/steveice10/mc/protocol/codec/MinecraftCodec.class"
	);

	@NotNull String resolve(@NotNull List<Path> nativeLibraries, @Nullable ClassLoader contextLoader) {
		String separator = File.pathSeparator;
		Set<String> entries = new LinkedHashSet<>(Arrays.asList(
				System.getProperty("java.class.path", "").split(Pattern.quote(separator))));

		for (ClassLoader loader = contextLoader; loader != null; loader = loader.getParent())
			if (loader instanceof URLClassLoader urls)
				for (URL url : urls.getURLs()) addFileUrl(entries, url);

		entries.removeIf(this::isProtocolRuntime);
		List<String> ordered = new ArrayList<>(entries.size() + 1);
		nativeLibraries.forEach(path -> ordered.add(path.toAbsolutePath().toString()));
		ordered.addAll(entries);
		return String.join(separator, ordered);
	}

	private boolean isProtocolRuntime(String entry) {
		if (entry.isBlank()) return true;

		Path path = Path.of(entry);
		if (Files.isDirectory(path))
			return CODEC_RESOURCES.stream().anyMatch(resource -> Files.isRegularFile(path.resolve(resource)));
		if (!Files.isRegularFile(path)) return false;

		try (JarFile jar = new JarFile(path.toFile())) {
			return CODEC_RESOURCES.stream().anyMatch(resource -> jar.getJarEntry(resource) != null);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not inspect worker classpath entry: " + path, exception);
		}
	}

	private void addFileUrl(Set<String> entries, URL url) {
		if (!url.getProtocol().equals("file"))
			throw new IllegalArgumentException("Worker classpath requires local file URLs: " + url);
		try {
			entries.add(Path.of(url.toURI()).toString());
		} catch (URISyntaxException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid worker classpath URL: " + url, exception);
		}
	}
}
