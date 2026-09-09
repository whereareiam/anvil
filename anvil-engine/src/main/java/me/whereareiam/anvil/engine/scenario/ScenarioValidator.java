package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates scenario declarations, process references, and launch requirements before scoped execution.
 */
public final class ScenarioValidator {
	/**
	 * Checks the complete scenario structure without acquiring process resources.
	 *
	 * @param scenario scenario declaration to validate
	 * @param eulaAccepted whether the caller has accepted the Mojang EULA
	 * @throws ScenarioValidationException if the declaration or launch requirements are invalid
	 */
	public void validate(@NotNull AnvilScenario scenario, boolean eulaAccepted) {
		validateDeclaration(scenario);
		validateBinding(scenario);
		validateEula(scenario, eulaAccepted);
		validateProcessReferences(scenario);
	}

	private void validateDeclaration(@NotNull AnvilScenario scenario) {
		if (blank(scenario.getName()))
			throw new ScenarioValidationException("Scenario name must not be blank");
		if (scenario.getServers().isEmpty() && scenario.getProxies().isEmpty())
			throw new ScenarioValidationException("Scenario '" + scenario.getName() + "' has no servers or proxies");
	}

	private void validateBinding(@NotNull AnvilScenario scenario) {
		try {
			if (InetAddress.getByName(scenario.getBindAddress()).isLoopbackAddress()) return;
		} catch (UnknownHostException failure) {
			throw new ScenarioValidationException("Invalid scenario bind address: " + scenario.getBindAddress(), failure);
		}
		if (!scenario.isManual() || !scenario.isAllowLanBinding())
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

		Set<String> processNames = new HashSet<>(serverNames);
		for (MinecraftProxy proxy : scenario.getProxies())
			registerProcessName(proxy.getName(), processNames);

		if (!processNames.contains(entrypoint))
			throw new ScenarioValidationException("Scenario entrypoint '" + entrypoint
					+ "' does not reference a server or proxy");

		for (MinecraftProxy proxy : scenario.getProxies())
			validateProxyRoutes(proxy, serverNames);
	}

	private void registerProcessName(@NotNull String name, @NotNull Set<String> names) {
		if (name.isBlank())
			throw new ScenarioValidationException("Every server and proxy needs a non-blank name");
		if (!names.add(name))
			throw new ScenarioValidationException("Duplicate server or proxy name: " + name);
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
