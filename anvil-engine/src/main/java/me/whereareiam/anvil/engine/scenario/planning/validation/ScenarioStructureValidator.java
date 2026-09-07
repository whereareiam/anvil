package me.whereareiam.anvil.engine.scenario.planning.validation;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates scenario-level structure independent of platform providers.
 */
public final class ScenarioStructureValidator {
    public void validate(@NotNull AnvilScenario scenario, boolean eulaAccepted) {
        if (blank(scenario.getName()))
            throw new ScenarioValidationException("Scenario name must not be blank");
        if (scenario.getServers().isEmpty() && scenario.getProxies().isEmpty())
            throw new ScenarioValidationException("Scenario '" + scenario.getName() + "' has no servers or proxies");
        validateBinding(scenario);
        if (!eulaAccepted && !scenario.getServers().isEmpty())
            throw new ScenarioValidationException("Mojang EULA acceptance is required before starting servers");
        if (blank(scenario.getEntrypoint()))
            throw new ScenarioValidationException("Scenario '" + scenario.getName() + "' must declare an entrypoint");
    }

    public void validateEntrypoint(@NotNull AnvilScenario scenario, @NotNull Map<String, ?> processes) {
        if (!processes.containsKey(scenario.getEntrypoint()))
            throw new ScenarioValidationException("Scenario entrypoint '" + scenario.getEntrypoint()
                    + "' does not reference a server or proxy");
    }

    public void validateProxies(@NotNull AnvilScenario scenario, @NotNull Map<String, MinecraftServer> servers) {
        for (MinecraftProxy proxy : scenario.getProxies()) {
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
