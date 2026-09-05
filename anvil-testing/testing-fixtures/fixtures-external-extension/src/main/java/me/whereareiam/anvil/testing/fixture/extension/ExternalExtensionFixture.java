package me.whereareiam.anvil.testing.fixture.extension;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Packages one fixture backend selection and scopes discovery to its external class loader.
 * Shared by runtime discovery checks and tests that install the same JAR into a real agent.
 */
public final class ExternalExtensionFixture implements AutoCloseable {
	private static final String PROTOCOL_SERVICE = "META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider";
	private final Path artifact;
	private final ClassLoader previous;
	private final URLClassLoader loader;

	public ExternalExtensionFixture(Path directory, String provider, boolean includeInstalledProtocols) throws Exception {
		Path source = Path.of(ExternalExtensionFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		artifact = packageProvider(source, directory, provider);
		previous = Thread.currentThread().getContextClassLoader();
		ClassLoader parent = new ClassLoader(previous) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (name.startsWith("external.fixture."))
					throw new ClassNotFoundException(name);
				return super.loadClass(name, resolve);
			}

			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				if (name.equals(PROTOCOL_SERVICE) && !includeInstalledProtocols)
					return Collections.emptyEnumeration();
				var resources = Collections.list(super.getResources(name));
				resources.removeIf(url -> belongsTo(url, source));
				return Collections.enumeration(resources);
			}
		};
		loader = new URLClassLoader(new URL[] {artifact.toUri().toURL()}, parent);
		Thread.currentThread().setContextClassLoader(loader);
	}

	public Path artifact() {
		return artifact;
	}

	public Class<?> load(String name) throws ClassNotFoundException {
		return loader.loadClass(name);
	}

	@Override
	public void close() throws IOException {
		Thread.currentThread().setContextClassLoader(previous);
		loader.close();
	}

	private Path packageProvider(Path source, Path directory, String provider) throws IOException {
		Files.createDirectories(directory);
		Path jar = directory.resolve("external-provider.jar");
		try (var input = new JarInputStream(Files.newInputStream(source));
			 var output = new JarOutputStream(Files.newOutputStream(jar))) {
			JarEntry entry;
			while ((entry = input.getNextJarEntry()) != null) {
				String name = entry.getName();
				if (entry.isDirectory() || name.equals(PROTOCOL_SERVICE))
					continue;
				if (!name.startsWith("external/fixture/") && !name.startsWith("META-INF/services/"))
					continue;
				output.putNextEntry(new JarEntry(name));
				input.transferTo(output);
				output.closeEntry();
			}
			output.putNextEntry(new JarEntry(PROTOCOL_SERVICE));
			output.write(("external.fixture.protocol." + provider + "\n").getBytes(StandardCharsets.UTF_8));
			output.closeEntry();
		}
		return jar;
	}

	private boolean belongsTo(URL resource, Path artifact) {
		try {
			if (!resource.getProtocol().equals("jar"))
				return false;
			var connection = (JarURLConnection) resource.openConnection();
			return Path.of(connection.getJarFileURL().toURI()).equals(artifact);
		} catch (IOException | URISyntaxException exception) {
			throw new IllegalStateException("Could not inspect fixture service resource " + resource, exception);
		}
	}
}
