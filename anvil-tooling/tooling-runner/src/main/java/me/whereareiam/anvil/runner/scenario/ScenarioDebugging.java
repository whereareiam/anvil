package me.whereareiam.anvil.runner.scenario;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds a JDWP agent to the processes a debugging client selected.
 * <p>
 * The agent listens on a free loopback port without suspending the JVM, and prints the port to the process
 * console when it starts, so a debugger can attach to each JVM generation.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScenarioDebugging {
	/**
	 * The JVM argument that loads the agent.
	 */
	public static final @NotNull String AGENT = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:0";

	/**
	 * Returns the scenario with the agent added to one process, or to every server and proxy.
	 *
	 * @param scenario the declared scenario
	 * @param process  the process to debug, or null for every server and proxy
	 * @return the scenario the runner prepares instead
	 */
	public static @NotNull AnvilScenario withAgent(@NotNull AnvilScenario scenario, @Nullable String process) {
		return scenario.toBuilder()
				.clearServers()
				.servers(scenario.getServers().stream().map(server -> debugs(server.getName(), process) ? agent(server) : server).toList())
				.clearProxies()
				.proxies(scenario.getProxies().stream().map(proxy -> debugs(proxy.getName(), process) ? agent(proxy) : proxy).toList())
				.build();
	}

	private static boolean debugs(@NotNull String name, @Nullable String process) {
		return process == null || process.equals(name);
	}

	private static @NotNull MinecraftServer agent(@NotNull MinecraftServer server) {
		return server.toBuilder().jvmArgument(AGENT).build();
	}

	private static @NotNull MinecraftProxy agent(@NotNull MinecraftProxy proxy) {
		return proxy.toBuilder().jvmArgument(AGENT).build();
	}
}
