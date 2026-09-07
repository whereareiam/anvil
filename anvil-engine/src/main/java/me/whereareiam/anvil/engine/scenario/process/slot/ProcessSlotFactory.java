package me.whereareiam.anvil.engine.scenario.process.slot;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.engine.provisioning.artifact.ScenarioArtifactResolver;
import me.whereareiam.anvil.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactStore;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

/**
 * Creates process slots from a prepared execution topology.
 */
@RequiredArgsConstructor
public final class ProcessSlotFactory {
    private final EngineOptions options;
    private final AnvilScenario scenario;
    private final Map<String, PlatformProvider> providers;
    private final ScenarioArtifactResolver artifacts;
    private final ArtifactStore downloads;
    private final AgentConnectionProvider agentConnections;
    private final Map<String, InetSocketAddress> processAddresses;
    private final Map<String, ForwardingConfiguration> forwarding;

    public @NotNull ProcessSlot create(
            @NotNull MinecraftProcess declaration,
            @NotNull ProcessTarget target,
            @NotNull Path workDirectory,
            @NotNull Path workspaceGroupDirectory
    ) {
        PlatformProvider provider = providers.get(declaration.getPlatform());
        PlatformContext context = PlatformContext.builder()
                .scenario(scenario)
                .cacheDirectory(options.getCacheDirectory())
                .workDirectory(workDirectory)
                .workspaceGroupDirectory(workspaceGroupDirectory)
                .bindAddress(target.bindAddress())
                .port(target.peerAddress().getPort())
                .processAddresses(processAddresses)
                .eulaAccepted(options.isEulaAccepted())
                .artifactResolver(downloads)
                .forwarding(forwarding.get(declaration.getName()))
                .build();

        WorkspacePlan workspacePlan = artifacts.installAgent(declaration.getWorkspace(), provider.platformAgent());

        return ProcessSlot.builder()
                .options(options)
                .declaration(declaration)
                .provider(provider)
                .context(context)
                .workspacePlan(workspacePlan)
                .agentConnections(agentConnections)
                .target(target)
                .build();
    }
}
