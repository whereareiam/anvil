package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.engine.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.engine.AnvilException;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Performs deterministic scenario preflight before downloads or process launches.
 */
public final class ScenarioValidator {
	/**
	 * Validates a complete scenario environment.
	 *
	 * @param scenario scenario to validate
	 * @param eulaAccepted whether Mojang's EULA was explicitly accepted
	 * @param providers discovered platform providers
	 */
	public void validate(
			AnvilScenario scenario,
			boolean eulaAccepted,
			Map<String, PlatformProvider> providers
	) {
		if (blank(scenario.getName()))
			throw new AnvilException("Scenario name must not be blank");
		if (scenario.getServers().isEmpty() && scenario.getProxies().isEmpty())
			throw new AnvilException("Scenario '" + scenario.getName() + "' has no servers or proxies");
		validateBinding(scenario);
		if (!eulaAccepted && !scenario.getServers().isEmpty())
			throw new AnvilException("Mojang EULA acceptance is required: set "
					+ EngineOptions.EULA_ACCEPTED_PROPERTY + "=true");

		Map<String, MinecraftProcess> processes = new LinkedHashMap<>();
		scenario.getServers().forEach(server -> register(server, processes, providers, scenario.isManual()));
		scenario.getProxies().forEach(proxy -> register(proxy, processes, providers, scenario.isManual()));

		if (blank(scenario.getEntrypoint()))
			throw new AnvilException("Scenario '" + scenario.getName() + "' must declare an entrypoint");
		if (!processes.containsKey(scenario.getEntrypoint()))
			throw new AnvilException("Scenario entrypoint '" + scenario.getEntrypoint()
					+ "' does not reference a server or proxy");

		Map<String, MinecraftServer> servers = new LinkedHashMap<>();
		scenario.getServers().forEach(server -> servers.put(server.getName(), server));
		for (MinecraftProxy proxy : scenario.getProxies())
			validateProxy(proxy, servers);
	}

	private void register(
			MinecraftProcess process,
			Map<String, MinecraftProcess> processes,
			Map<String, PlatformProvider> providers,
			boolean manual
	) {
		if (blank(process.getName()))
			throw new AnvilException("Every server and proxy needs a non-blank name");
		if (processes.putIfAbsent(process.getName(), process) != null)
			throw new AnvilException("Duplicate server or proxy name: " + process.getName());

		PlatformProvider provider = providers.get(process.getPlatform());
		if (provider == null)
			throw new AnvilException("No PlatformProvider for '" + process.getPlatform()
					+ "'. Available: " + providers.keySet());
		if (!provider.configurationType().isInstance(process))
			throw new AnvilException("Platform '" + process.getPlatform() + "' requires "
					+ provider.configurationType().getSimpleName() + " but '" + process.getName()
					+ "' is " + process.getClass().getSimpleName());
		if (process.getDistribution() == null)
			throw new AnvilException("Process '" + process.getName() + "' has no distribution");
		if (!process.getDistribution().isLocal() && !process.getDistribution().isArtifact()
				&& blank(process.getDistribution().getVersion()))
			throw new AnvilException("Remote distribution for process '" + process.getName()
					+ "' requires a version");
		try {
			provider.validateDistribution(process);
		} catch (PlatformException exception) {
			throw new AnvilException("Invalid distribution for process '" + process.getName() + "': "
					+ exception.getMessage(), exception);
		}
		if (process instanceof MinecraftServer server
				&& (process.getDistribution().isLocal() || process.getDistribution().isArtifact())
				&& blank(server.getMinecraftVersion()))
			throw new AnvilException("Local or artifact server distribution for '" + process.getName()
					+ "' requires minecraftVersion");
		if (!manual && process.getDistribution().isLatest())
			throw new AnvilException("Automated scenario process '" + process.getName()
					+ "' must pin an immutable build instead of 'latest'");
		if (manual && process.getDistribution().isLatest())
			System.err.println("[Anvil] Warning: manual process '" + process.getName()
					+ "' selects latest; this run is not reproducible.");
		if (process.getMemoryMegabytes() < 256)
			throw new AnvilException("Process '" + process.getName() + "' must allocate at least 256 MiB");
	}

	private void validateProxy(MinecraftProxy proxy, Map<String, MinecraftServer> servers) {
		if (proxy.getServers().isEmpty())
			throw new AnvilException("Proxy '" + proxy.getName() + "' has no registered servers");

		Set<String> unique = new HashSet<>();
		for (String server : proxy.getServers()) {
			if (!unique.add(server))
				throw new AnvilException("Proxy '" + proxy.getName() + "' registers server '"
						+ server + "' more than once");
			if (!servers.containsKey(server))
				throw new AnvilException("Proxy '" + proxy.getName() + "' references missing server '"
						+ server + "'");
		}
		if (blank(proxy.getDefaultServer()))
			throw new AnvilException("Proxy '" + proxy.getName() + "' must declare a default server");
		if (!unique.contains(proxy.getDefaultServer()))
			throw new AnvilException("Proxy '" + proxy.getName() + "' default server '"
					+ proxy.getDefaultServer() + "' is not registered with that proxy");
	}

	private void validateBinding(AnvilScenario scenario) {
		try {
			if (InetAddress.getByName(scenario.getBindAddress()).isLoopbackAddress())
				return;
		} catch (UnknownHostException e) {
			throw new AnvilException("Invalid scenario bind address: " + scenario.getBindAddress(), e);
		}
		if (!scenario.isManual() || !scenario.isAllowLanBinding())
			throw new AnvilException("Non-loopback binding requires a manual scenario and allowLanBinding=true");
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
