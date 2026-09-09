package me.whereareiam.anvil.testkit.support;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Loads a prepared extension JAR while preserving the host's shared API classes.
 * The creating thread owns the loader and must close it after discovery and extension use finish.
 */
public final class TestExtensionLoader implements AutoCloseable {
	private static final String PROTOCOL_SERVICE = "META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider";

	private final Path artifact;
	private final ClassLoader previous;
	private final URLClassLoader loader;

	/**
	 * Installs the extension as the current thread's context loader.
	 *
	 * @param artifact the prepared fixture JAR supplied by the test task
	 * @param includeInstalledProtocols whether parent protocol provider descriptors remain visible
	 */
	public TestExtensionLoader(@NotNull Path artifact, boolean includeInstalledProtocols) throws IOException {
		this.artifact = artifact;
		previous = Thread.currentThread().getContextClassLoader();
		ClassLoader parent = new ClassLoader(previous) {
			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				if (name.equals(PROTOCOL_SERVICE) && !includeInstalledProtocols)
					return Collections.emptyEnumeration();

				return super.getResources(name);
			}
		};
		loader = new URLClassLoader(new URL[] {artifact.toUri().toURL()}, parent);
		Thread.currentThread().setContextClassLoader(loader);
	}

	public @NotNull Path artifact() {
		return artifact;
	}

	public @NotNull Class<?> load(@NotNull String name) throws ClassNotFoundException {
		return loader.loadClass(name);
	}

	@Override
	public void close() throws IOException {
		Thread.currentThread().setContextClassLoader(previous);
		loader.close();
	}
}
