package me.whereareiam.anvil.agent.common.transport;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.operation.AgentOperationProvider;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Owns the operation-extension class loader installed into one managed platform workspace.
 */
final class AgentExtensionLoader implements AutoCloseable {
	private final URLClassLoader loader;

	AgentExtensionLoader(Path directory, ClassLoader parent) {
		try {
			List<URL> artifacts = new ArrayList<>();
			if (Files.isDirectory(directory))
				try (var paths = Files.list(directory)) {
					for (Path path : paths.filter(Files::isRegularFile)
							.filter(path -> path.getFileName().toString().endsWith(".jar")).sorted().toList())
						artifacts.add(path.toUri().toURL());
				}
			loader = new URLClassLoader(artifacts.toArray(URL[]::new), parent);
		} catch (IOException exception) {
			throw new AgentException("Could not load agent extensions from " + directory, exception);
		}
	}

	List<AgentOperationProvider> providers() {
		return ServiceLoader.load(AgentOperationProvider.class, loader).stream()
				.map(ServiceLoader.Provider::get).toList();
	}

	@Override
	public void close() {
		try {
			loader.close();
		} catch (IOException exception) {
			throw new AgentException("Could not close the agent extension class loader", exception);
		}
	}
}
