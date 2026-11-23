package me.whereareiam.anvil.platform.planning;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Lets a debugger attach to the servers and proxies the engine options name: each gets a listener named
 * {@value #LISTENER}, on which its JVM's debug agent listens, so its port is reserved, published and mirrored like
 * any listener's. A process that waits for its debugger gets a startup deadline long enough to attach.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProcessDebugging {
	/**
	 * Name of the listener a debugger attaches to.
	 */
	static final String LISTENER = "debug";
	private static final String EVERY = "*";
	private static final Duration SUSPENDED_STARTUP = Duration.ofMinutes(30);

	/**
	 * Returns the scenario with its debugged processes prepared for a debugger.
	 *
	 * @param scenario declared scenario
	 * @param options engine options naming the debugged processes
	 * @return the scenario, unchanged when it debugs none of its processes
	 */
	static @NotNull AnvilScenario apply(@NotNull AnvilScenario scenario, @NotNull EngineOptions options) {
		if (options.debugProcesses().isEmpty()) return scenario;

		boolean[] debugged = {false};
		var servers = scenario.servers().stream().map(server -> {
			if (!debugs(options, server.name())) return server;
			debugged[0] = true;
			return server.toBuilder().listener(LISTENER).jvmArgument(agent(server.name(), options)).build();
		}).toList();
		var proxies = scenario.proxies().stream().map(proxy -> {
			if (!debugs(options, proxy.name())) return proxy;
			debugged[0] = true;
			return proxy.toBuilder().listener(LISTENER).jvmArgument(agent(proxy.name(), options)).build();
		}).toList();
		if (!debugged[0]) return scenario;

		var debuggable = scenario.toBuilder().clearServers().servers(servers).clearProxies().proxies(proxies);
		if (options.debugSuspend()) debuggable.processTimeouts(longStartup(scenario.processTimeouts()));
		return debuggable.build();
	}

	private static boolean debugs(EngineOptions options, String name) {
		return options.debugProcesses().contains(EVERY) || options.debugProcesses().contains(name);
	}

	private static String agent(String process, EngineOptions options) {
		return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (options.debugSuspend() ? "y" : "n")
				+ ",address=*:${anvil.process." + process + ".listener." + LISTENER + "}";
	}

	private static ProcessTimeouts longStartup(ProcessTimeouts timeouts) {
		return timeouts.toBuilder().startup(timeouts.startup() == null || timeouts.startup().compareTo(SUSPENDED_STARTUP) < 0
				? SUSPENDED_STARTUP : timeouts.startup()).build();
	}
}
