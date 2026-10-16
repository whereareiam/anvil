package me.whereareiam.anvil.agent.lumi;

import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginBase;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Internal Lumi plugin that starts the Anvil agent in a class loader of its own.
 *
 * <p>Lumi ships an older Jackson and its plugin class loader asks the server first, so the agent would see that
 * Jackson if it ran in the plugin's class loader. The plugin therefore only carries the agent, the agent server and
 * its Jackson as a nested JAR: it copies that JAR into its data folder, loads it through an
 * {@link AgentClassLoader}, and removes both again when Lumi disables it.
 */
public final class AnvilLumiAgent extends PluginBase {
	private static final String AGENT_JAR = "/META-INF/anvil/agent.jar";
	private static final String AGENT_CLASS = "me.whereareiam.anvil.agent.lumi.LumiPlatformAgent";

	private Path jar;
	private AgentClassLoader loader;
	private AutoCloseable agent;

	@Override
	public void onEnable() {
		try {
			jar = extract();
			loader = new AgentClassLoader(jar, AnvilLumiAgent.class.getClassLoader());
			agent = loader.loadClass(AGENT_CLASS).asSubclass(AutoCloseable.class)
					.getConstructor(Plugin.class)
					.newInstance(this);
		} catch (Exception | LinkageError failure) {
			Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
			try {
				stop();
			} catch (RuntimeException cleanup) {
				cause.addSuppressed(cleanup);
			}
			throw new IllegalStateException("Could not start the Anvil agent", cause);
		}
	}

	@Override
	public void onDisable() {
		stop();
	}

	private Path extract() throws IOException {
		Path target = getDataFolder().toPath().resolve("agent.jar");
		Files.createDirectories(target.getParent());
		try (InputStream source = AnvilLumiAgent.class.getResourceAsStream(AGENT_JAR)) {
			if (source == null)
				throw new IOException("The plugin JAR does not contain " + AGENT_JAR);
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}

		return target;
	}

	/**
	 * Stops the agent, then closes its class loader, then removes its JAR, attempting each even when an earlier
	 * one fails.
	 */
	private void stop() {
		Path removed = jar;
		AgentClassLoader closed = loader;
		AutoCloseable stopped = agent;
		jar = null;
		loader = null;
		agent = null;
		try (AutoCloseable file = () -> {
			if (removed != null)
				Files.deleteIfExists(removed);
		}; AutoCloseable classes = closed; AutoCloseable server = stopped) {
			// Resources close in reverse order: the agent, its class loader, then its JAR.
		} catch (Exception exception) {
			throw new IllegalStateException("Could not stop the Anvil agent", exception);
		}
	}
}
