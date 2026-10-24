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
		if (options.getDebugProcesses().isEmpty()) return scenario;

		boolean[] debugged = {false};
		var servers = scenario.getServers().stream().map(server -> {
			if (!debugs(options, server.getName())) return server;
			debugged[0] = true;
			return server.toBuilder().listener(LISTENER).jvmArgument(agent(server.getName(), options)).build();
		}).toList();
		var proxies = scenario.getProxies().stream().map(proxy -> {
			if (!debugs(options, proxy.getName())) return proxy;
			debugged[0] = true;
			return proxy.toBuilder().listener(LISTENER).jvmArgument(agent(proxy.getName(), options)).build();
		}).toList();
		if (!debugged[0]) return scenario;

		var debuggable = scenario.toBuilder().clearServers().servers(servers).clearProxies().proxies(proxies);
		if (options.isDebugSuspend()) debuggable.processTimeouts(longStartup(scenario.getProcessTimeouts()));
		return debuggable.build();
	}

	private static boolean debugs(EngineOptions options, String name) {
		return options.getDebugProcesses().contains(EVERY) || options.getDebugProcesses().contains(name);
	}

	private static String agent(String process, EngineOptions options) {
		return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (options.isDebugSuspend() ? "y" : "n")
				+ ",address=*:${anvil.process." + process + ".listener." + LISTENER + "}";
	}

	private static ProcessTimeouts longStartup(ProcessTimeouts timeouts) {
		return timeouts.toBuilder().startup(timeouts.getStartup() == null || timeouts.getStartup().compareTo(SUSPENDED_STARTUP) < 0
				? SUSPENDED_STARTUP : timeouts.getStartup()).build();
	}
}
