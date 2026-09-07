package me.whereareiam.anvil.engine.scenario.session;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.engine.player.RunningPlayerManager;
import me.whereareiam.anvil.engine.provisioning.artifact.ScenarioArtifactResolver;
import me.whereareiam.anvil.engine.provisioning.workspace.WorkspaceFiles;
import me.whereareiam.anvil.engine.scenario.process.ProcessRegistry;
import me.whereareiam.anvil.engine.scenario.process.slot.ProcessSlotFactory;
import me.whereareiam.anvil.engine.scenario.process.StartupScheduler;
import me.whereareiam.anvil.engine.scenario.topology.ForwardingPlan;
import me.whereareiam.anvil.execution.api.ExecutionProvider;
import me.whereareiam.anvil.execution.api.ExecutionSession;
import me.whereareiam.anvil.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.execution.api.model.ProcessRequest;
import me.whereareiam.anvil.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactStore;
import me.whereareiam.anvil.provisioning.api.JavaProvisioner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Owns resources acquired for one scenario session, including startup and dependency-ordered release.
 */
@RequiredArgsConstructor
final class SessionResources {
    private final EngineOptions options;
    private final AnvilScenario scenario;
    private final ScenarioWorkspaceLayout workspace;
    private final WorkspaceFiles files = new WorkspaceFiles();
    private final ProcessRegistry processes = new ProcessRegistry();
    private @Nullable ExecutionSession executionSession;
    private @Nullable PlayerManager players;
    private boolean failed;
    private boolean closed;

    @NotNull ProcessRegistry processes() {
        return processes;
    }

    void open(
            @NotNull ForwardingPlan forwardingPlan,
            @NotNull Map<String, PlatformProvider> providers,
            @NotNull ScenarioArtifactResolver artifacts,
            @NotNull AgentConnectionProvider connections,
            @NotNull Supplier<ProtocolBackend> backend,
            @NotNull ProtocolPlayerComposer composer,
            @NotNull ArtifactStore downloads,
            @NotNull JavaProvisioner java,
            @NotNull ExecutionProvider execution
    ) {
        files.recreate(options.getWorkDirectory(), workspace.runDirectory());
        ProtocolBackend protocol = backend.get();
        List<MinecraftProcess> declarations = Stream.concat(
                scenario.getServers().stream().map(MinecraftProcess.class::cast),
                scenario.getProxies().stream().map(MinecraftProcess.class::cast)
        ).toList();

        ExecutionSession executionSession = execution.open(ExecutionContext.builder()
                .cacheDirectory(options.getCacheDirectory())
                .bindAddress(scenario.getBindAddress())
                .javaValidator(java)
                .artifacts(downloads)
                .offline(options.isOffline())
                .refresh(options.isRefresh())
                .networkPolicy(scenario.getNetworkPolicy())
                .build());
        this.executionSession = executionSession;

        Map<String, ProcessTarget> targets = new ConcurrentHashMap<>();
        StartupScheduler tasks = new StartupScheduler(options.getParallelism(), options.getStartupMemoryMegabytes());
        tasks.run(declarations, declaration -> {
            PlatformProvider provider = providers.get(declaration.getPlatform());
            ProcessTarget target = executionSession.prepare(ProcessRequest.builder()
                    .name(declaration.getName())
                    .workspace(workspace.processDirectory(declaration))
                    .javaRequirement(javaRequirement(declaration))
                    .javaSource(javaSource(declaration))
                    .minimumJavaVersion(provider.minimumJavaVersion(declaration))
                    .agent(provider.platformAgent() != null)
                    .publishGame(!(declaration instanceof MinecraftServer)
                            || scenario.getNetworkPolicy().getBackendNetworkExposure() != NetworkExposure.PRIVATE)
                    .build());
            targets.put(declaration.getName(), target);
        });

        Map<String, InetSocketAddress> addresses = new LinkedHashMap<>();
        targets.forEach((name, target) -> addresses.put(name, target.peerAddress()));
        Map<String, ForwardingConfiguration> forwarding = forwarding(forwardingPlan);
        ProcessSlotFactory slots = new ProcessSlotFactory(options, scenario, providers, artifacts, downloads,
                connections, addresses, forwarding);

        for (MinecraftProcess declaration : declarations)
            processes.register(slots.create(declaration, targets.get(declaration.getName()),
                    workspace.processDirectory(declaration), workspace.runDirectory()));

        tasks.run(declarations, declaration -> processes.prepare(declaration.getName()));
        for (MinecraftProcess declaration : declarations) processes.configure(declaration.getName());
        tasks.start(scenario.getServers(), declaration -> processes.start(declaration.getName()));
        tasks.start(scenario.getProxies(), declaration -> processes.start(declaration.getName()));

        players = new RunningPlayerManager(scenario, protocol, processes.currentByName(), processes.agents(), composer);
    }

    @NotNull PlayerManager players() {
        if (players == null) throw new IllegalStateException("Players are not initialized");
        return players;
    }

    void markFailed() {
        failed = true;
    }

    void close(boolean successful) {
        synchronized (this) {
            if (closed) return;
            closed = true;

            List<Throwable> failures = new ArrayList<>();
            if (players != null) attempt(players::close, failures);

            attempt(() -> processes.close(successful && !failed && failures.isEmpty()), failures);
            if (executionSession != null) attempt(executionSession::close, failures);
            if ((successful && !failed && !processes.failed() && failures.isEmpty()) || !options.isKeepFailedWorkspaces())
                attempt(() -> files.deleteAbsolute(workspace.runDirectory()), failures);

            if (failures.isEmpty()) return;

            Throwable first = failures.getFirst();
            for (Throwable failure : failures.subList(1, failures.size()))
                if (failure != first) first.addSuppressed(failure);

            if (first instanceof Error error) throw error;
            throw (RuntimeException) first;
        }
    }

    private JavaRequirement javaRequirement(@NotNull MinecraftProcess process) {
        if (process.getJavaRequirement() != null) return process.getJavaRequirement();
        if (scenario.getJavaRequirement() != null) return scenario.getJavaRequirement();

        return options.getJavaRequirement();
    }

    private JavaSource javaSource(@NotNull MinecraftProcess process) {
        if (process.getJavaSource() != null) return process.getJavaSource();
        if (scenario.getJavaSource() != null) return scenario.getJavaSource();

        return options.getJavaSource();
    }

    private @NotNull Map<String, ForwardingConfiguration> forwarding(@NotNull ForwardingPlan plan) {
        Map<String, ForwardingConfiguration> result = new LinkedHashMap<>();
        SecureRandom random = new SecureRandom();

        for (ForwardingPlan.Group group : plan.groups()) {
            byte[] secret = new byte[32];
            random.nextBytes(secret);
            ForwardingConfiguration configuration = ForwardingConfiguration.builder()
                    .mode(group.mode())
                    .proxyOnlineMode(group.proxyOnlineMode())
                    .secret(group.mode() == ForwardingMode.NONE
							? null
							: HexFormat.of().formatHex(secret))
                    .build();

            group.processes().forEach(name -> result.put(name, configuration));
        }

        return result;
    }

    private void attempt(@NotNull Runnable action, @NotNull List<Throwable> failures) {
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            failures.add(failure);
        }
    }
}
