package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.engine.model.EngineOptions;

import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.runtime.AnvilContext;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.agent.api.transport.AgentArtifactLocator;
import me.whereareiam.anvil.agent.api.transport.connection.AgentConnectionProvider;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProviderRegistry;
import me.whereareiam.anvil.engine.runtime.process.ScenarioProcessLauncher;
import me.whereareiam.anvil.engine.scenario.RunningAnvilContext;
import me.whereareiam.anvil.engine.scenario.ScenarioResources;
import me.whereareiam.anvil.engine.provisioning.ScenarioArtifactResolver;
import me.whereareiam.anvil.engine.player.RunningPlayerManager;
import me.whereareiam.anvil.engine.provisioning.WorkspaceFiles;
import me.whereareiam.anvil.engine.scenario.ScenarioValidator;
import me.whereareiam.anvil.engine.scenario.ForwardingPlanner;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Starts reproducible local Minecraft scenarios and creates real protocol players on demand.
 *
 * <pre>{@code
 * try (AnvilEngine engine = new AnvilEngine(options);
 *      AnvilContext context = engine.start(scenario)) {
 *     SimulatedPlayer alice = context.players().create("Alice");
 *     Session session = alice.capability(Session.class);
 *     session.connect();
 *     session.connected(Duration.ofSeconds(30));
 * }
 * }</pre>
 */
public final class AnvilEngine implements AutoCloseable {
	private final EngineOptions options;
	private final Map<String, PlatformProvider> providers;
	private final ProtocolPlayerComposer playerComposer;
	private final ProtocolProvider protocolProvider;
	private final ScenarioProcessLauncher processLauncher;
	private final ScenarioArtifactResolver artifacts;
	@Nullable
	private ProtocolBackend protocolBackend;
	private final WorkspaceFiles workspaceFiles = new WorkspaceFiles();
	private final ScenarioValidator validator = new ScenarioValidator();
	private final List<RunningAnvilContext> contexts = new ArrayList<>();
	private boolean closed;

	/**
	 * Discovers platform and protocol providers from the current context class loader.
	 */
	public AnvilEngine(@NotNull EngineOptions options) {
		this(options, ProtocolProviderRegistry.discover().select(options.getProtocolId()));
	}

	private AnvilEngine(@NotNull EngineOptions options, @NotNull ProtocolProvider protocolProvider) {
		this.options = options;
		this.protocolProvider = protocolProvider;
		this.playerComposer = ProtocolPlayerComposer.discover(protocolProvider.id());
		this.providers = discoverPlatforms();
		AgentConnectionProvider agentConnections = discoverService(AgentConnectionProvider.class, "agent connection provider");
		AgentArtifactLocator agentArtifacts = discoverService(AgentArtifactLocator.class, "agent artifact locator");
		this.artifacts = new ScenarioArtifactResolver(options.getArtifacts(), agentArtifacts);
		this.processLauncher = new ScenarioProcessLauncher(options, artifacts, agentConnections);
	}

	/**
	 * Provisions and starts every server and proxy declared by a scenario.
	 *
	 * @param scenario scenario definition
	 * @return running context; closing it destroys players and stops the complete process group
	 */
	public synchronized @NotNull AnvilContext start(@NotNull AnvilScenario scenario) {
		if (closed) throw new AnvilException("Cannot start a scenario after the engine is closed");

		AnvilScenario effectiveScenario = artifacts.resolve(scenario);
		validator.validate(effectiveScenario, options.isEulaAccepted(), providers);
		var forwarding = new ForwardingPlanner().plan(effectiveScenario, providers);
		ProtocolBackend backend = protocolBackend();

		Path root = options.getWorkDirectory().toAbsolutePath().normalize();
		String runId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
		Path runDirectory = root.resolve(WorkspaceFiles.safeName(scenario.getName())).resolve(runId);
		workspaceFiles.recreate(root, runDirectory);
		ScenarioResources resources = new ScenarioResources(
				options.getStopTimeout(), successful -> finalizeRun(runDirectory, successful)
		);

		List<MinecraftProcess> declarations = processes(effectiveScenario);
		Map<String, Integer> processPorts = new LinkedHashMap<>();
		declarations.forEach(process -> processPorts.put(
				process.getName(),
				resources.allocatePort(effectiveScenario.getBindAddress())
		));

		RunningAnvilContext context = null;
		try {
			for (MinecraftProcess declaration : declarations)
				processLauncher.start(declaration, providers.get(declaration.getPlatform()),
						effectiveScenario, runDirectory, processPorts, forwarding.get(declaration.getName()), resources);

			RunningPlayerManager players = new RunningPlayerManager(
					effectiveScenario,
					backend,
					resources.processes(),
					resources.agents(),
					playerComposer
			);
			context = new RunningAnvilContext(effectiveScenario, players, resources);
			contexts.add(context);
			if (effectiveScenario.getSetupHook() != null)
				effectiveScenario.getSetupHook().execute(context);
			return context;
		} catch (Exception exception) {
			try {
				if (context != null)
					contexts.remove(context);
				resources.close(false);
			} catch (RuntimeException cleanupFailure) {
				exception.addSuppressed(cleanupFailure);
			}
			if (exception instanceof AnvilException anvilException)
				throw anvilException;
			throw new AnvilException("Could not start scenario '" + scenario.getName() + "'", exception);
		}
	}

	private void finalizeRun(Path runDirectory, boolean successful) {
		if (successful || !options.isKeepFailedWorkspaces())
			workspaceFiles.deleteAbsolute(runDirectory);
	}

	private List<MinecraftProcess> processes(AnvilScenario scenario) {
		return Stream.concat(
				scenario.getServers().stream().map(MinecraftProcess.class::cast),
				scenario.getProxies().stream().map(MinecraftProcess.class::cast)
		).toList();
	}

	private static Map<String, PlatformProvider> discoverPlatforms() {
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		List<PlatformProvider> discovered = contextLoader == null
				? List.of()
				: ServiceLoader.load(PlatformProvider.class, contextLoader).stream()
				.map(ServiceLoader.Provider::get)
				.toList();

		if (discovered.isEmpty())
			discovered = ServiceLoader.load(PlatformProvider.class, AnvilEngine.class.getClassLoader()).stream()
					.map(ServiceLoader.Provider::get)
					.toList();

		return discovered.stream()
			.collect(Collectors.toUnmodifiableMap(PlatformProvider::id, provider -> provider));
	}

	private static <T> @NotNull T discoverService(@NotNull Class<T> type, @NotNull String description) {
		ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
		T service = contextLoader == null
				? null
				: ServiceLoader.load(type, contextLoader).findFirst().orElse(null);

		if (service != null) return service;
		return ServiceLoader.load(type, AnvilEngine.class.getClassLoader())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No Anvil " + description + " is installed"));
	}

	private synchronized @NotNull ProtocolBackend protocolBackend() {
		if (protocolBackend == null)
			protocolBackend = protocolProvider.create(options.getCacheDirectory());

		return protocolBackend;
	}

	/**
	 * Stops every still-running context and closes the protocol backend.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;
		closed = true;

		RuntimeException failure = null;
		for (RunningAnvilContext context : contexts)
			try {
				context.close();
			} catch (RuntimeException exception) {
				failure = retainFailure(failure, exception);
			}

		contexts.clear();
		if (protocolBackend != null)
			try {
				protocolBackend.close();
			} catch (RuntimeException exception) {
				failure = retainFailure(failure, exception);
			}

		if (failure != null) throw failure;
	}

	private @NotNull RuntimeException retainFailure(
			@Nullable RuntimeException failure,
			@NotNull RuntimeException exception
	) {
		if (failure == null) return exception;
		if (failure != exception) failure.addSuppressed(exception);

		return failure;
	}
}
