package me.whereareiam.anvil.engine.scenario.session;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.engine.EngineDefaults;
import me.whereareiam.anvil.engine.provisioning.artifact.ScenarioArtifactResolver;
import me.whereareiam.anvil.engine.scenario.planning.ScenarioPlan;
import me.whereareiam.anvil.engine.scenario.planning.ScenarioPlanner;
import me.whereareiam.anvil.execution.api.ExecutionProvider;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactStore;
import me.whereareiam.anvil.provisioning.api.JavaProvisioner;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Exposes one active scenario and coordinates setup with its owned resource graph.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScenarioSession implements ScenarioContext {
    private final AnvilScenario scenario;
    private final SessionResources resources;

    /**
     * Validates a scenario and acquires its resources before executing setup.
     * Failure rolls back every acquired resource and preserves cleanup failures.
     *
     * @param options          engine options
     * @param scenario         requested scenario
     * @param providers        installed platform providers
     * @param playerComposer   selected protocol-player composer
     * @param artifacts        named artifact and agent lookup
     * @param agentConnections host connections to platform agents
     * @param backend          lazy access to the engine-owned backend
     * @param downloads        shared verified artifact store
     * @param java             Java runtime provisioner
     * @param execution        selected execution provider
     * @return context after readiness and setup complete
     */
    @Builder(buildMethodName = "start")
    public static @NotNull ScenarioSession start(
            @NotNull EngineOptions options,
            @NotNull AnvilScenario scenario,
            @NotNull Map<String, PlatformProvider> providers,
            @NotNull ProtocolPlayerComposer playerComposer,
            @NotNull ScenarioArtifactResolver artifacts,
            @NotNull AgentConnectionProvider agentConnections,
            @NotNull Supplier<ProtocolBackend> backend,
            @NotNull ArtifactStore downloads,
            @NotNull JavaProvisioner java,
            @NotNull ExecutionProvider execution
    ) {
        EngineOptions effectiveOptions = EngineDefaults.resolve(options);
        AnvilScenario effective = artifacts.resolve(scenario);
        ScenarioPlan plan = new ScenarioPlanner().plan(effective, effectiveOptions.isEulaAccepted(), providers);
        ScenarioWorkspaceLayout workspace = ScenarioWorkspaceLayout.create(effectiveOptions, effective);
        SessionResources resources = new SessionResources(effectiveOptions, effective, workspace);
        ScenarioSession session = new ScenarioSession(effective, resources);

        try {
            resources.open(plan.forwarding(), providers, artifacts, agentConnections, backend,
                    playerComposer, downloads, java, execution);
            session.executeSetup();
            return session;
        } catch (RuntimeException | Error failure) {
            session.rollback(failure);
            throw failure;
        }
    }

    @Override
    public @NotNull AnvilScenario definition() {
        return scenario;
    }

    @Override
    public @NotNull ScenarioProcesses processes() {
        return resources.processes();
    }

    @Override
    public @NotNull PlayerManager players() {
        return resources.players();
    }

    @Override
    public void close() {
        close(true);
    }

    /**
     * Finalizes the session using explicit execution success and any recorded lifecycle failures.
     *
     * @param successful whether the caller completed normally
     */
    public void close(boolean successful) {
        resources.close(successful);
    }

    private void executeSetup() {
        if (scenario.getSetupHook() == null) return;

        try {
            scenario.getSetupHook().execute(this);
        } catch (AnvilException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new ScenarioStartupException(scenario.getName(), "Setup failed for scenario '" + scenario.getName() + "'", failure);
        }
    }

    private void rollback(@NotNull Throwable failure) {
        resources.markFailed();
        try {
            close(false);
        } catch (RuntimeException | Error cleanup) {
            if (cleanup != failure) failure.addSuppressed(cleanup);
        }
    }
}
