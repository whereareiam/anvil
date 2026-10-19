package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.ContainerProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.RemoteEndpoint;
import me.whereareiam.anvil.api.type.ProcessLifetime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates scenario declarations, process and remote references, and launch requirements before scoped execution.
 */
public final class ScenarioValidator {
	/** Names of containers and listeners, which their address placeholders carry. */
	private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

	/**
	 * Checks the complete scenario structure without acquiring process resources.
	 *
	 * @param scenario scenario declaration to validate
	 * @param eulaAccepted whether the caller has accepted the Mojang EULA
	 * @throws ScenarioValidationException if the declaration or launch requirements are invalid
	 */
	public void validate(@NotNull AnvilScenario scenario, boolean eulaAccepted) {
		validateDeclaration(scenario);
		validateTimeout("Startup", scenario.getProcessTimeouts().getStartup());
		validateTimeout("Shutdown", scenario.getProcessTimeouts().getShutdown());
		validateBinding(scenario);
		validateEula(scenario, eulaAccepted);
		validateProcessReferences(scenario);
	}

	private void validateDeclaration(@NotNull AnvilScenario scenario) {
		if (blank(scenario.getName()))
			throw new ScenarioValidationException("Scenario name must not be blank");
		if (scenario.getServers().isEmpty() && scenario.getProxies().isEmpty() && scenario.getRemotes().isEmpty())
			throw new ScenarioValidationException("Scenario '" + scenario.getName() + "' has no servers, proxies or remotes");
	}

	private void validateTimeout(String name, @Nullable Duration timeout) {
		if (timeout != null && (timeout.isZero() || timeout.isNegative()))
			throw new ScenarioValidationException(name + " timeout must be positive");
	}

	private void validateBinding(@NotNull AnvilScenario scenario) {
		try {
			if (InetAddress.getByName(scenario.getNetworkPolicy().getBindAddress()).isLoopbackAddress()) return;
		} catch (UnknownHostException failure) {
			throw new ScenarioValidationException("Invalid scenario bind address: " + scenario.getNetworkPolicy().getBindAddress(), failure);
		}
		if (!scenario.isManual() || !scenario.getNetworkPolicy().isAllowLanBinding())
			throw new ScenarioValidationException("Non-loopback binding requires a manual scenario and allowLanBinding=true");
	}

	private void validateEula(@NotNull AnvilScenario scenario, boolean eulaAccepted) {
		if (!eulaAccepted && !scenario.getServers().isEmpty())
			throw new ScenarioValidationException("Mojang EULA acceptance is required before starting servers");
	}

	private void validateProcessReferences(@NotNull AnvilScenario scenario) {
		String entrypoint = scenario.getEntrypoint();
		if (blank(entrypoint))
			throw new ScenarioValidationException("Scenario '" + scenario.getName() + "' must declare an entrypoint");

		Set<String> serverNames = new HashSet<>();
		for (MinecraftServer server : scenario.getServers())
			registerProcessName(server.getName(), serverNames);
		scenario.getServers().forEach(this::validateListeners);
		scenario.getProxies().forEach(this::validateListeners);

		Set<String> targetNames = new HashSet<>(serverNames);
		for (MinecraftProxy proxy : scenario.getProxies())
			registerProcessName(proxy.getName(), targetNames);
		for (RemoteEndpoint remote : scenario.getRemotes()) {
			registerRemoteName(remote.getName(), targetNames);
			validateRemote(remote);
		}
		Set<String> names = new HashSet<>(targetNames);
		for (ContainerProcess container : scenario.getContainers())
			validateContainer(container, names);

		if (!targetNames.contains(entrypoint))
			throw new ScenarioValidationException("Scenario entrypoint '" + entrypoint
					+ "' does not reference a server, proxy or remote");

		for (MinecraftProxy proxy : scenario.getProxies())
			validateProxyRoutes(proxy, serverNames);
		validateLifetimes(scenario);
	}

	/**
	 * A proxy that outlives its scenario cannot route to a server that stops with it.
	 */
	private void validateLifetimes(@NotNull AnvilScenario scenario) {
		Set<String> scenarioServers = new HashSet<>();
		for (MinecraftServer server : scenario.getServers())
			if (server.getLifetime() == ProcessLifetime.SCENARIO) scenarioServers.add(server.getName());

		for (MinecraftProxy proxy : scenario.getProxies()) {
			if (proxy.getLifetime() != ProcessLifetime.ENGINE) continue;

			for (String server : proxy.getServers())
				if (scenarioServers.contains(server))
					throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' keeps running for the engine, so its server '"
							+ server + "' must declare the engine lifetime as well");
		}
	}

	private void registerProcessName(@NotNull String name, @NotNull Set<String> names) {
		if (name.isBlank())
			throw new ScenarioValidationException("Every server and proxy needs a non-blank name");
		if (!names.add(name))
			throw new ScenarioValidationException("Duplicate server or proxy name: " + name);
	}

	private void registerRemoteName(@NotNull String name, @NotNull Set<String> names) {
		if (name.isBlank())
			throw new ScenarioValidationException("Every remote needs a non-blank name");
		if (!names.add(name))
			throw new ScenarioValidationException("Remote '" + name + "' shares its name with another server, proxy or remote");
	}

	/**
	 * A container is a process the other processes reach by its name.
	 */
	private void validateContainer(@NotNull ContainerProcess container, @NotNull Set<String> names) {
		String name = container.getName();
		if (!NAME.matcher(name).matches())
			throw new ScenarioValidationException("Container name '" + name
					+ "' must be lowercase letters, digits and dashes, starting with a letter or digit");
		if (!names.add(name))
			throw new ScenarioValidationException("Container '" + name + "' shares its name with another process or remote");
		if (blank(container.getImage()))
			throw new ScenarioValidationException("Container '" + name + "' needs an image");
		if (container.getPorts().isEmpty())
			throw new ScenarioValidationException("Container '" + name + "' needs at least one port");
		if (container.getMemoryMegabytes() < 1)
			throw new ScenarioValidationException("Container '" + name + "' needs a positive memory");

		Set<Integer> ports = new HashSet<>();
		for (int port : container.getPorts())
			if (port < 1 || port > 65535 || !ports.add(port))
				throw new ScenarioValidationException("Container '" + name + "' declares port " + port + " twice or outside 1-65535");

		if (container.getReadyLog() == null) return;
		try {
			Pattern.compile(container.getReadyLog());
		} catch (PatternSyntaxException invalid) {
			throw new ScenarioValidationException("Container '" + name + "' has an invalid ready log pattern", invalid);
		}
	}

	/**
	 * A listener's name is part of its address placeholder.
	 */
	private void validateListeners(@NotNull MinecraftProcess process) {
		Set<String> listeners = new HashSet<>();
		for (String listener : process.getListeners()) {
			if (!NAME.matcher(listener).matches())
				throw new ScenarioValidationException("Listener '" + listener + "' of process '" + process.getName()
						+ "' must be lowercase letters, digits and dashes, starting with a letter or digit");
			if (!listeners.add(listener))
				throw new ScenarioValidationException("Process '" + process.getName() + "' declares listener '" + listener + "' twice");
		}
	}

	/**
	 * Parses what players need from a remote before they connect: its host and port, and the version they speak.
	 */
	private void validateRemote(@NotNull RemoteEndpoint remote) {
		try {
			remote.host();
			remote.port();
			remote.nativeVersion();
		} catch (IllegalArgumentException invalid) {
			throw new ScenarioValidationException(invalid.getMessage(), invalid);
		}
	}

	private void validateProxyRoutes(@NotNull MinecraftProxy proxy, @NotNull Set<String> serverNames) {
		if (proxy.getServers().isEmpty())
			throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' has no registered servers");

		Set<String> registered = new HashSet<>();
		for (String server : proxy.getServers()) {
			if (!registered.add(server))
				throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' registers server '"
						+ server + "' more than once");
			if (!serverNames.contains(server))
				throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' references missing server '"
						+ server + "'");
		}

		if (blank(proxy.getDefaultServer()))
			throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' must declare a default server");
		if (!registered.contains(proxy.getDefaultServer()))
			throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' default server '"
					+ proxy.getDefaultServer() + "' is not registered with that proxy");
	}

	private boolean blank(@Nullable String value) {
		return value == null || value.isBlank();
	}
}
