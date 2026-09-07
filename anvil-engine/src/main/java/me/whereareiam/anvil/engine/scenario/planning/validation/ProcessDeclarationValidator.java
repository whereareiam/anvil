package me.whereareiam.anvil.engine.scenario.planning.validation;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates process declarations against their selected platform providers.
 */
public final class ProcessDeclarationValidator {
    public @NotNull Map<String, MinecraftProcess> validate(
            @NotNull AnvilScenario scenario,
            @NotNull Map<String, PlatformProvider> providers
    ) {
        Map<String, MinecraftProcess> processes = new LinkedHashMap<>();
        scenario.getServers().forEach(server -> register(server, processes, providers, scenario.isManual()));
        scenario.getProxies().forEach(proxy -> register(proxy, processes, providers, scenario.isManual()));
        return processes;
    }

    private void register(
            @NotNull MinecraftProcess process,
            @NotNull Map<String, MinecraftProcess> processes,
            @NotNull Map<String, PlatformProvider> providers,
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
        if (!process.getDistribution().isLocal() && !process.getDistribution().isArtifact()
                && blank(process.getDistribution().getVersion()))
            throw new ScenarioValidationException("Remote distribution for process '" + process.getName()
                    + "' requires a version");

        try {
            provider.validateDistribution(process);
        } catch (PlatformException failure) {
            throw new ScenarioValidationException("Invalid distribution for process '" + process.getName() + "': "
                    + failure.getMessage(), failure);
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
