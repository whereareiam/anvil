package me.whereareiam.anvil.engine.player;

import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.agent.api.transport.AgentDirectory;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.engine.process.ManagedProcess;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.type.ProtocolCapability;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default context-owned simulated-player factory and registry.
 */
public final class RunningPlayerManager implements PlayerManager {
	private final AnvilScenario scenario;
	private final ProtocolBackend backend;
	private final Map<String, ManagedProcess> processes;
	private final Map<String, AgentClient> agents;
	private final Map<String, MinecraftProcess> declarations;
	private final Map<String, MinecraftServer> servers;
	private final Map<String, ProtocolSupport> protocols;
	private final ProtocolPlayerComposer playerComposer;
	private final Map<String, SimulatedPlayer> players = new ConcurrentHashMap<>();
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Creates a player manager for an already-running scenario.
	 *
	 * @param scenario running scenario
	 * @param backend simulated-player protocol backend
	 * @param processes running processes by scenario name
	 */
	public RunningPlayerManager(
			@NotNull AnvilScenario scenario,
			@NotNull ProtocolBackend backend,
			@NotNull Map<String, ManagedProcess> processes
	) {
		this(scenario, backend, processes, Map.of(), ProtocolPlayerComposer.discover(backend.id()));
	}

	/**
	 * Creates a player manager using an explicit capability runtime.
	 *
	 * @param scenario running scenario
	 * @param backend simulated-player protocol backend
	 * @param processes running processes by scenario name
	 * @param playerComposer player-facade composer
	 */
	public RunningPlayerManager(
			@NotNull AnvilScenario scenario,
			@NotNull ProtocolBackend backend,
			@NotNull Map<String, ManagedProcess> processes,
			@NotNull ProtocolPlayerComposer playerComposer
	) {
		this(scenario, backend, processes, Map.of(), playerComposer);
	}

	/**
	 * Creates a player manager using authenticated platform agents and discovered capabilities.
	 *
	 * @param scenario running scenario
	 * @param backend simulated-player protocol backend
	 * @param processes running processes by scenario name
	 * @param agents authenticated platform-agent connections by process name
	 */
	public RunningPlayerManager(
			@NotNull AnvilScenario scenario,
			@NotNull ProtocolBackend backend,
			@NotNull Map<String, ManagedProcess> processes,
			@NotNull Map<String, AgentClient> agents
	) {
		this(scenario, backend, processes, agents, ProtocolPlayerComposer.discover(backend.id()));
	}

	/**
	 * Creates a player manager using an explicit agent map and capability runtime.
	 *
	 * @param scenario running scenario
	 * @param backend simulated-player protocol backend
	 * @param processes running processes by scenario name
	 * @param agents authenticated platform-agent connections by process name
	 * @param playerComposer player-facade composer
	 */
	public RunningPlayerManager(
			@NotNull AnvilScenario scenario,
			@NotNull ProtocolBackend backend,
			@NotNull Map<String, ManagedProcess> processes,
			@NotNull Map<String, AgentClient> agents,
			@NotNull ProtocolPlayerComposer playerComposer
	) {
		this.scenario = scenario;
		this.backend = backend;
		this.processes = Map.copyOf(processes);
		this.agents = Map.copyOf(agents);
		this.playerComposer = playerComposer;
		this.declarations = declarations(scenario);
		this.servers = scenario.getServers().stream()
				.collect(Collectors.toUnmodifiableMap(MinecraftServer::getName, Function.identity()));
		this.protocols = backend.supportedProtocols().stream()
				.collect(Collectors.toUnmodifiableMap(ProtocolSupport::getMinecraftVersion, Function.identity()));
	}

	@Override
	public @NotNull SimulatedPlayer create(@NotNull String name) {
		return create(PlayerOptions.builder().name(name).build());
	}

	@Override
	public synchronized @NotNull SimulatedPlayer create(@NotNull PlayerOptions options) {
		ensureOpen();
		if (options.getName().isBlank())
			throw new IllegalArgumentException("Simulated player name must not be blank");
		if (players.containsKey(options.getName()))
			throw new IllegalArgumentException("Simulated player '" + options.getName() + "' already exists");

		String targetName = options.getConnectTo() == null ? scenario.getEntrypoint() : options.getConnectTo();
		MinecraftProcess target = declarations.get(targetName);
		ManagedProcess runningTarget = processes.get(targetName);
		if (target == null || runningTarget == null)
			throw new ScenarioValidationException("Player '" + options.getName() + "' cannot connect to unknown process '"
					+ targetName + "'. Available: " + declarations.keySet());

		String version = selectVersion(options, target);
		ProtocolSupport support = protocols.get(version);
		if (support == null)
			throw new ScenarioValidationException("Unsupported clientVersion '" + version + "'. Supported: "
					+ protocols.keySet());
		validateAuthentication(options, target, support);

		ProtocolPlayer driven = backend.create(PlayerRequest.builder()
				.name(options.getName())
				.clientVersion(version)
				.address(runningTarget.address())
				.authentication(options.getAuthentication())
				.authenticationProfile(options.getAuthenticationProfile())
				.build());
		Set<String> serverNames = Set.copyOf(servers.keySet());
		SimulatedPlayer player;
		try {
			RunningPlayerObservation observation = new RunningPlayerObservation(driven, agents, serverNames);
			AgentDirectory directory = () -> agents;
			player = playerComposer.compose(
					driven,
					List.of(observation, directory),
					destroyed -> players.remove(options.getName(), destroyed)
			);
		} catch (RuntimeException exception) {
			driven.destroy();
			throw exception;
		}
		players.put(options.getName(), player);
		return player;
	}

	@Override
	public @NotNull Collection<SimulatedPlayer> all() {
		return Collections.unmodifiableCollection(new ArrayList<>(players.values()));
	}

	@Override
	public @NotNull SimulatedPlayer get(@NotNull String name) {
		SimulatedPlayer player = players.get(name);
		if (player == null)
			throw new NoSuchElementException("Unknown simulated player '" + name + "'. Available: " + players.keySet());
		return player;
	}

	@Override
	public void destroyAll() {
		RuntimeException failure = null;
		for (SimulatedPlayer player : all())
			try {
				player.destroy();
			} catch (RuntimeException exception) {
				if (failure == null) {
					failure = exception;
					continue;
				}
				if (failure != exception)
					failure.addSuppressed(exception);
			}
		players.clear();
		if (failure != null)
			throw failure;
	}

	/**
	 * Prevents further creation and destroys every remaining player.
	 */
	public void close() {
		if (!closed.compareAndSet(false, true))
			return;
		destroyAll();
	}

	private String selectVersion(PlayerOptions options, MinecraftProcess target) {
		Set<String> nativeVersions = reachableServers(target).stream()
				.map(this::nativeVersion)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (nativeVersions.isEmpty())
			throw new ScenarioValidationException("Connection target '" + target.getName() + "' reaches no Minecraft servers");
		if (nativeVersions.size() > 1)
			throw new ScenarioValidationException("Connection target '" + target.getName()
					+ "' reaches servers with different native versions " + nativeVersions
					+ "; no exact-fidelity client can traverse all of them");

		String compatible = nativeVersions.iterator().next();
		if (options.getClientVersion() == null)
			return compatible;
		if (!options.getClientVersion().equals(compatible))
			throw new ScenarioValidationException("Native client version '" + options.getClientVersion()
					+ "' does not match servers reachable through '" + target.getName()
					+ "' using version '" + compatible + "'");
		return options.getClientVersion();
	}

	private Collection<MinecraftServer> reachableServers(MinecraftProcess target) {
		if (target instanceof MinecraftServer server)
			return List.of(server);
		MinecraftProxy proxy = (MinecraftProxy) target;
		return proxy.getServers().stream().map(servers::get).toList();
	}

	private String nativeVersion(MinecraftServer server) {
		String version = server.getDistribution().isLocal() || server.getDistribution().isArtifact()
				? server.getMinecraftVersion()
				: server.getDistribution().getVersion();
		if (version == null || version.isBlank())
			throw new ScenarioValidationException("Server '" + server.getName() + "' does not declare its native Minecraft version");
		return version;
	}

	private void validateAuthentication(
			PlayerOptions options,
			MinecraftProcess target,
			ProtocolSupport support
	) {
		if (options.getAuthentication() == AuthenticationMode.OFFLINE && target.isOnlineMode())
			throw new ScenarioValidationException("Offline player '" + options.getName()
					+ "' cannot join online-mode process '" + target.getName() + "'");
		if (options.getAuthentication() != AuthenticationMode.ONLINE)
			return;
		if (!target.isOnlineMode())
			throw new ScenarioValidationException("Online player '" + options.getName()
					+ "' requires an online-mode entrypoint");
		if (options.getAuthenticationProfile() == null || options.getAuthenticationProfile().isBlank())
			throw new ScenarioValidationException("Online player '" + options.getName() + "' requires an authentication profile");
		if (!support.getCapabilities().contains(ProtocolCapability.ONLINE_AUTHENTICATION))
			throw new ScenarioValidationException("Client version '" + support.getMinecraftVersion()
					+ "' does not support online authentication");
	}

	private Map<String, MinecraftProcess> declarations(AnvilScenario scenario) {
		Map<String, MinecraftProcess> result = new LinkedHashMap<>();
		scenario.getServers().forEach(server -> result.put(server.getName(), server));
		scenario.getProxies().forEach(proxy -> result.put(proxy.getName(), proxy));
		return Map.copyOf(result);
	}

	private void ensureOpen() {
		if (closed.get())
			throw new IllegalStateException("Anvil scenario '" + scenario.getName() + "' is closed");
	}
}
