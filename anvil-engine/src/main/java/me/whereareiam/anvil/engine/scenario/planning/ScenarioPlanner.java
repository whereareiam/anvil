package me.whereareiam.anvil.engine.scenario.planning;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.engine.scenario.topology.ForwardingNegotiator;
import me.whereareiam.anvil.engine.scenario.topology.ForwardingPlan;
import me.whereareiam.anvil.engine.scenario.topology.ProcessTopology;
import me.whereareiam.anvil.engine.scenario.planning.validation.ProcessDeclarationValidator;
import me.whereareiam.anvil.engine.scenario.planning.validation.ScenarioStructureValidator;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Performs deterministic scenario preflight before downloads or process launches.
 */
public final class ScenarioPlanner {
    private final ScenarioStructureValidator structure = new ScenarioStructureValidator();
    private final ProcessDeclarationValidator declarations = new ProcessDeclarationValidator();

    /**
     * Validates a complete scenario environment.
     *
     * @param scenario     scenario to validate
     * @param eulaAccepted whether Mojang's EULA was explicitly accepted
     * @param providers    discovered platform providers
     * @return immutable scenario plan
     */
    public @NotNull ScenarioPlan plan(
            @NotNull AnvilScenario scenario,
            boolean eulaAccepted,
            @NotNull Map<String, PlatformProvider> providers
    ) {
        structure.validate(scenario, eulaAccepted);
        Map<String, MinecraftProcess> processes = declarations.validate(scenario, providers);
        structure.validateEntrypoint(scenario, processes);

        Map<String, MinecraftServer> servers = new LinkedHashMap<>();
        scenario.getServers().forEach(server -> servers.put(server.getName(), server));
        structure.validateProxies(scenario, servers);

        ForwardingPlan forwarding = new ForwardingNegotiator().negotiate(ProcessTopology.from(scenario), providers);
        return new ScenarioPlan(scenario, forwarding);
    }

}
