package me.whereareiam.anvil.agent.client.transport;

import me.whereareiam.anvil.agent.client.api.AgentArtifactLocator;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * Locates the exact agent JAR supplied on the runtime classpath.
 */
public final class ClasspathAgentArtifactLocator implements AgentArtifactLocator {
	@Override
	public @NotNull Path locate(@NotNull String className) {
		try {
			String resourceName = className.replace('.', '/') + ".class";
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader == null) loader = ClasspathAgentArtifactLocator.class.getClassLoader();

			URL resource = loader.getResource(resourceName);
			if (resource == null) throw new AgentException("Platform agent is missing from the runtime classpath: " + className);

			if (!resource.getProtocol().equals("jar"))
				throw new AgentException("Platform agent must be supplied as a packaged JAR on the runtime classpath: "
						+ className + " (loaded from " + resource + ")");

			URL jar = ((JarURLConnection) resource.openConnection()).getJarFileURL();
			return Path.of(jar.toURI());
		} catch (URISyntaxException | IOException exception) {
			throw new AgentException("Could not locate platform agent " + className, exception);
		}
	}
}
