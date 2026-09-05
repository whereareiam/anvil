package me.whereareiam.anvil.engine.scenario.preflight;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs deterministic scenario preflight before downloads or process launches.
 */
public final class ScenarioPreflight {
	/**
	 * Validates a complete scenario environment.
	 *
	 * @param scenario scenario to validate
	 * @param eulaAccepted whether Mojang's EULA was explicitly accepted
	 * @param providers discovered platform providers
	 * @return immutable connected forwarding groups
	 */
	public @NotNull List<ForwardingGroup> plan(
			@NotNull AnvilScenario scenario,
			boolean eulaAccepted,
			@NotNull Map<String, PlatformProvider> providers
	) {
		if (blank(scenario.getName()))
			throw new ScenarioValidationException("Scenario name must not be blank");
		if (scenario.getServers().isEmpty() && scenario.getProxies().isEmpty())
			throw new ScenarioValidationException("Scenario '" + scenario.getName() + "' has no servers or proxies");
		validateBinding(scenario);
		if (!eulaAccepted && !scenario.getServers().isEmpty())
			throw new ScenarioValidationException("Mojang EULA acceptance is required before starting servers");

		Map<String, MinecraftProcess> processes = new LinkedHashMap<>();
		scenario.getServers().forEach(server -> register(server, processes, providers, scenario.isManual()));
		scenario.getProxies().forEach(proxy -> register(proxy, processes, providers, scenario.isManual()));

		if (blank(scenario.getEntrypoint()))
			throw new ScenarioValidationException("Scenario '" + scenario.getName() + "' must declare an entrypoint");
		if (!processes.containsKey(scenario.getEntrypoint()))
			throw new ScenarioValidationException("Scenario entrypoint '" + scenario.getEntrypoint()
					+ "' does not reference a server or proxy");

		Map<String, MinecraftServer> servers = new LinkedHashMap<>();
		scenario.getServers().forEach(server -> servers.put(server.getName(), server));
		for (MinecraftProxy proxy : scenario.getProxies())
			validateProxy(proxy, servers);

		return new ForwardingPlanner().plan(scenario, providers);
	}

	private void register(
			MinecraftProcess process,
			Map<String, MinecraftProcess> processes,
			Map<String, PlatformProvider> providers,
			boolean manual
	) {
		if (blank(process.getName()))
			throw new ScenarioValidationException("Every server and proxy needs a non-blank name");
		if (processes.putIfAbsent(process.getName(), process) != null)
			throw new ScenarioValidationException("Duplicate server or proxy name: " + process.getName());

		PlatformProvider provider = providers.get(process.getPlatform());
		if (provider == null)
			throw new ScenarioValidationException("No PlatformProvider for '" + process.getPlatform()
					+ "'. Available: " + providers.keySet());
		if (!provider.configurationType().isInstance(process))
			throw new ScenarioValidationException("Platform '" + process.getPlatform() + "' requires "
					+ provider.configurationType().getSimpleName() + " but '" + process.getName()
					+ "' is " + process.getClass().getSimpleName());
        process.getDistribution();
        if (!process.getDistribution().isLocal() && !process.getDistribution().isArtifact()
				&& blank(process.getDistribution().getVersion()))
			throw new ScenarioValidationException("Remote distribution for process '" + process.getName()
					+ "' requires a version");
		try {
			provider.validateDistribution(process);
		} catch (PlatformException exception) {
			throw new ScenarioValidationException("Invalid distribution for process '" + process.getName() + "': "
					+ exception.getMessage(), exception);
		}
		if (process instanceof MinecraftServer server
				&& (process.getDistribution().isLocal() || process.getDistribution().isArtifact())
				&& blank(server.getMinecraftVersion()))
			throw new ScenarioValidationException("Local or artifact server distribution for '" + process.getName()
					+ "' requires minecraftVersion");
		if (!manual && process.getDistribution().isLatest())
			throw new ScenarioValidationException("Automated scenario process '" + process.getName()
					+ "' must pin an immutable build instead of 'latest'");
		if (manual && process.getDistribution().isLatest())
			System.err.println("[Anvil] Warning: manual process '" + process.getName()
					+ "' selects latest; this run is not reproducible.");
		if (process.getMemoryMegabytes() < 256)
			throw new ScenarioValidationException("Process '" + process.getName() + "' must allocate at least 256 MiB");
	}

	private void validateProxy(MinecraftProxy proxy, Map<String, MinecraftServer> servers) {
		if (proxy.getServers().isEmpty())
			throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' has no registered servers");

		Set<String> unique = new HashSet<>();
		for (String server : proxy.getServers()) {
			if (!unique.add(server))
				throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' registers server '"
						+ server + "' more than once");
			if (!servers.containsKey(server))
				throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' references missing server '"
						+ server + "'");
		}
		if (blank(proxy.getDefaultServer()))
			throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' must declare a default server");
		if (!unique.contains(proxy.getDefaultServer()))
			throw new ScenarioValidationException("Proxy '" + proxy.getName() + "' default server '"
					+ proxy.getDefaultServer() + "' is not registered with that proxy");
	}

	private void validateBinding(AnvilScenario scenario) {
		try {
			if (InetAddress.getByName(scenario.getBindAddress()).isLoopbackAddress())
				return;
		} catch (UnknownHostException e) {
			throw new ScenarioValidationException("Invalid scenario bind address: " + scenario.getBindAddress(), e);
		}
		if (!scenario.isManual() || !scenario.isAllowLanBinding())
			throw new ScenarioValidationException("Non-loopback binding requires a manual scenario and allowLanBinding=true");
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * Negotiated process roles for one connected component, before run credentials are created.
	 */
	public record ForwardingGroup(@NotNull Set<String> processes, @NotNull ForwardingMode mode, boolean proxyOnlineMode) { }
}
