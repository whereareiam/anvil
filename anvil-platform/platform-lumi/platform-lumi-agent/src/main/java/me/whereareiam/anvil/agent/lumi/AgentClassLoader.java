package me.whereareiam.anvil.agent.lumi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Loads the agent from its own JAR before asking Lumi, so the agent server and the extensions it loads use the
 * Jackson the agent carries rather than the older one Lumi ships.
 *
 * <p>Only the Java platform and Lumi's API ({@code cn.nukkit}) come from the plugin's class loader first: the agent
 * runs on them, and a copy of either in the agent JAR must not replace the server's. Every other class and resource
 * comes from the agent JAR, falling back to the plugin's class loader when the JAR lacks it.
 */
final class AgentClassLoader extends URLClassLoader {
	private static final List<String> SHARED_PACKAGES = List.of("java.", "cn.nukkit.");

	static {
		registerAsParallelCapable();
	}

	AgentClassLoader(@NotNull Path agent, @NotNull ClassLoader plugin) throws IOException {
		super("anvil-lumi-agent", new URL[] {agent.toUri().toURL()}, plugin);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> loaded = findLoadedClass(name);
			if (loaded == null)
				loaded = shared(name) ? getParent().loadClass(name) : agentFirst(name);
			if (resolve)
				resolveClass(loaded);

			return loaded;
		}
	}

	@Override
	public @Nullable URL getResource(String name) {
		URL resource = findResource(name);
		return resource != null ? resource : getParent().getResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		List<URL> resources = Collections.list(findResources(name));
		resources.addAll(Collections.list(getParent().getResources(name)));
		return Collections.enumeration(resources);
	}

	private Class<?> agentFirst(String name) throws ClassNotFoundException {
		try {
			return findClass(name);
		} catch (ClassNotFoundException missing) {
			return getParent().loadClass(name);
		}
	}

	private static boolean shared(String name) {
		return SHARED_PACKAGES.stream().anyMatch(name::startsWith);
	}
}
