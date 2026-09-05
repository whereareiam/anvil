package me.whereareiam.anvil.engine.scenario;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.AnvilContext;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.engine.EngineDefaults;
import me.whereareiam.anvil.engine.player.RunningPlayerManager;
import me.whereareiam.anvil.engine.process.PortSelection;
import me.whereareiam.anvil.engine.provisioning.artifact.DownloadCache;
import me.whereareiam.anvil.engine.provisioning.artifact.ScenarioArtifactResolver;
import me.whereareiam.anvil.engine.provisioning.java.ProcessJavaResolver;
import me.whereareiam.anvil.engine.provisioning.java.TemurinRuntimeProvisioner;
import me.whereareiam.anvil.engine.provisioning.workspace.WorkspaceFiles;
import me.whereareiam.anvil.engine.scenario.preflight.ScenarioPreflight.ForwardingGroup;
import me.whereareiam.anvil.engine.scenario.preflight.ScenarioPreflight;
import me.whereareiam.anvil.engine.scenario.process.RunningScenarioProcesses;
import me.whereareiam.anvil.engine.scenario.process.ScenarioProcess;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.ForwardingConfiguration;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Owns one scenario from resource acquisition through setup, process replacement, and cleanup.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScenarioRun implements AnvilContext {
	private final EngineOptions options;
	private final AnvilScenario scenario;
	private final Path runDirectory;
	private final RunningScenarioProcesses processes = new RunningScenarioProcesses();
	private final WorkspaceFiles files = new WorkspaceFiles();
	private final PortSelection ports = new PortSelection();

	private @Nullable PlayerManager players;
	private boolean failed;
	private boolean closed;

	/**
	 * Validates a scenario and creates a run that owns every acquired process and workspace.
	 * Failure rolls back the run, retaining the primary error and suppressed cleanup failures.
	 *
	 * @param options engine options
	 * @param scenario requested scenario
	 * @param providers installed platform providers
	 * @param playerComposer selected protocol-player composer
	 * @param artifacts named artifact and agent lookup
	 * @param agentConnections host connections to platform agents
	 * @param backend lazy access to the engine-owned backend
	 * @return context after readiness and setup complete
	 */
	@Builder(builderMethodName = "builder", buildMethodName = "start")
	public static @NotNull ScenarioRun start(
			@NotNull EngineOptions options,
			@NotNull AnvilScenario scenario,
			@NotNull Map<String, PlatformProvider> providers,
			@NotNull ProtocolPlayerComposer playerComposer,
			@NotNull ScenarioArtifactResolver artifacts,
			@NotNull AgentConnectionProvider agentConnections,
			@NotNull Supplier<ProtocolBackend> backend
	) {
		EngineOptions effectiveOptions = EngineDefaults.resolve(options);
		AnvilScenario effective = artifacts.resolve(scenario);
		List<ForwardingGroup> groups = new ScenarioPreflight().plan(effective, effectiveOptions.isEulaAccepted(), providers);
		String runId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
		Path directory = effectiveOptions.getWorkDirectory().toAbsolutePath().normalize()
				.resolve(WorkspaceFiles.safeName(effective.getName())).resolve(runId);
		ScenarioRun run = new ScenarioRun(effectiveOptions, effective, directory);

		try {
			run.launch(groups, providers, artifacts, agentConnections, backend, playerComposer);
			run.executeSetup();
			return run;
		} catch (RuntimeException | Error failure) {
			run.rollback(failure);
			throw failure;
		}
	}

	@Override
	public @NotNull AnvilScenario scenario() {
		return scenario;
	}

	@Override
	public @NotNull ScenarioProcesses processes() {
		return processes;
	}

	@Override
	public @NotNull PlayerManager players() {
		if (players == null) throw new IllegalStateException("Players are not initialized");
		return players;
	}

	@Override
	public void close() {
		close(true);
	}

	/**
	 * Finalizes the run using explicit execution success, in addition to any startup/restart failures.
	 *
	 * @param successful whether the caller completed normally
	 */
	public void close(boolean successful) {
		synchronized (processes) {
			if (closed) return;
			closed = true;

			List<Throwable> failures = new ArrayList<>();
			if (players != null) attempt(players::close, failures);
			attempt(() -> processes.close(successful && !failed && failures.isEmpty()), failures);
			if ((successful && !failed && !processes.failed() && failures.isEmpty()) || !options.isKeepFailedWorkspaces())
				attempt(() -> files.deleteAbsolute(runDirectory), failures);
			if (failures.isEmpty()) return;

			Throwable first = failures.getFirst();
			for (Throwable failure : failures.subList(1, failures.size()))
				if (failure != first) first.addSuppressed(failure);

			if (first instanceof Error error) throw error;
			throw (RuntimeException) first;
		}
	}

	private void launch(
			List<ForwardingGroup> groups,
			Map<String, PlatformProvider> providers,
			ScenarioArtifactResolver artifacts,
			AgentConnectionProvider connections,
			Supplier<ProtocolBackend> backend,
			ProtocolPlayerComposer composer
	) {
		files.recreate(options.getWorkDirectory(), runDirectory);
		ProtocolBackend protocol = backend.get();
		List<MinecraftProcess> declarations = Stream.concat(
				scenario.getServers().stream().map(MinecraftProcess.class::cast),
				scenario.getProxies().stream().map(MinecraftProcess.class::cast)
		).toList();
		Map<String, Integer> listeners = new LinkedHashMap<>();
		declarations.forEach(process -> listeners.put(process.getName(), ports.select(scenario.getBindAddress())));
		Map<String, Integer> processPorts = Map.copyOf(listeners);
		Map<String, ForwardingConfiguration> forwarding = forwarding(groups);
		DownloadCache downloads = new DownloadCache();
		ProcessJavaResolver java = new ProcessJavaResolver(options, new TemurinRuntimeProvisioner(downloads));
		Map<Integer, Path> javaExecutables = new LinkedHashMap<>();

		for (MinecraftProcess declaration : declarations) {
			PlatformProvider provider = providers.get(declaration.getPlatform());
			Path executable = javaExecutables.computeIfAbsent(provider.minimumJavaVersion(declaration), java::resolve);
			PlatformContext context = PlatformContext.builder()
					.scenario(scenario)
					.cacheDirectory(options.getCacheDirectory())
					.workDirectory(workspaceDirectory(declaration))
					.workspaceGroupDirectory(runDirectory)
					.bindAddress(scenario.getBindAddress())
					.port(processPorts.get(declaration.getName()))
					.processPorts(processPorts)
					.javaExecutable(executable)
					.eulaAccepted(options.isEulaAccepted())
					.artifactResolver(downloads)
					.forwarding(forwarding.get(declaration.getName()))
					.build();
			ScenarioProcess process = ScenarioProcess.builder()
					.options(options)
					.declaration(declaration)
					.provider(provider)
					.context(context)
					.workspacePlan(artifacts.installAgent(declaration.getWorkspace(), provider.platformAgent()))
					.agentConnections(connections)
					.ports(ports)
					.build();
			processes.start(process);
		}
		players = new RunningPlayerManager(scenario, protocol, processes.currentByName(), processes.agents(), composer);
	}

	private Path workspaceDirectory(MinecraftProcess process) {
		if (process.getWorkspace().getMode() != WorkspaceMode.PERSISTENT)
			return runDirectory.resolve(WorkspaceFiles.safeName(process.getName()));

		return options.getWorkDirectory().toAbsolutePath().normalize()
				.resolve(WorkspaceFiles.safeName(scenario.getName())).resolve("persistent")
				.resolve(WorkspaceFiles.safeName(process.getName()));
	}

	private Map<String, ForwardingConfiguration> forwarding(List<ForwardingGroup> groups) {
		Map<String, ForwardingConfiguration> result = new LinkedHashMap<>();
		SecureRandom random = new SecureRandom();
		for (ForwardingGroup group : groups) {
			byte[] secret = new byte[32];
			random.nextBytes(secret);
			ForwardingConfiguration configuration = ForwardingConfiguration.builder()
					.mode(group.mode())
					.proxyOnlineMode(group.proxyOnlineMode())
					.secret(group.mode() == ForwardingMode.NONE ? null : HexFormat.of().formatHex(secret))
					.build();
			group.processes().forEach(name -> result.put(name, configuration));
		}

		return result;
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

	private void rollback(Throwable failure) {
		failed = true;
		try {
			close(false);
		} catch (RuntimeException | Error cleanup) {
			if (cleanup != failure) failure.addSuppressed(cleanup);
		}
	}

	private void attempt(Runnable action, List<Throwable> failures) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failures.add(failure);
		}
	}
}
