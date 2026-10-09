package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.player.account.AccountPool.AccountLease;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.PlayerObservationFactory;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.type.ProtocolCapability;
import me.whereareiam.anvil.protocol.player.account.AccountReservations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default context-owned simulated-player factory and registry.
 */
final class RunningPlayerManager implements PlayerManager, AccountManager {
	private final AnvilScenario scenario;
	private final ProtocolBackend backend;
	private final ScenarioProcesses processes;
	private final PlayerObservationFactory observations;
	private final Map<String, MinecraftProcess> declarations;
	private final Map<String, MinecraftServer> servers;
	private final Map<String, ProtocolSupport> protocols;
	private final ProtocolPlayerComposer playerComposer;
	private final Consumer<RunningPlayerManager> onClosed;
	private final AccountManager accounts;
	private final AccountReservations reservations;

	private final Map<String, SimulatedPlayer> players = new ConcurrentHashMap<>();
	// Engine-wide account releases per online player: a claimed pool lease or a direct reservation.
	// Destruction runs them on the destroying thread without the manager lock, so a destroy callback can
	// never wait behind a create blocked on the backend.
	private final Map<String, Runnable> accountReleases = new ConcurrentHashMap<>();
	private boolean closed;

	RunningPlayerManager(
			@NotNull AnvilScenario scenario,
			@NotNull ProtocolBackend backend,
			@NotNull ScenarioProcesses processes,
			@NotNull PlayerObservationFactory observations,
			@NotNull ProtocolPlayerComposer playerComposer,
			@NotNull Consumer<RunningPlayerManager> onClosed,
			@NotNull AccountManager accounts,
			@NotNull AccountReservations reservations
	) {
		this.scenario = scenario;
		this.backend = backend;
		this.processes = processes;
		this.observations = observations;
		this.playerComposer = playerComposer;
		this.onClosed = onClosed;
		this.accounts = accounts;
		this.reservations = reservations;
		this.declarations = declarations(scenario);
		this.servers = scenario.getServers().stream()
				.collect(Collectors.toUnmodifiableMap(MinecraftServer::getName, Function.identity()));
		this.protocols = backend.supportedProtocols().stream()
				.collect(Collectors.toUnmodifiableMap(ProtocolSupport::getMinecraftVersion, Function.identity()));
	}

	RunningPlayerManager(
			@NotNull AnvilScenario scenario,
			@NotNull ProtocolBackend backend,
			@NotNull ScenarioProcesses processes,
			@NotNull PlayerObservationFactory observations,
			@NotNull ProtocolPlayerComposer playerComposer,
			@NotNull Consumer<RunningPlayerManager> onClosed
	) {
		this(scenario, backend, processes, observations, playerComposer, onClosed, List::of, new AccountReservations());
	}

	@Override
	public @NotNull Collection<AuthenticationAccount> list() { return accounts.list(); }

	@Override
	public @NotNull AccountPool pool(@NotNull Collection<String> accountIds) { return accounts.pool(accountIds); }

	@Override
	public @NotNull AccountPool pool(@NotNull String poolName) { return accounts.pool(poolName); }

	@Override
	public @NotNull SimulatedPlayer create(@NotNull String name) {
		return create(PlayerOptions.builder().name(name).build());
	}

	@Override
	public synchronized @NotNull SimulatedPlayer create(@NotNull String name, @NotNull AccountLease lease) {
		if (!lease.claim())
			throw new IllegalStateException("Account lease for '" + lease.account().getAccountId() + "' is no longer available");

		// Recorded before the player exists, so a player destroyed immediately still returns its lease.
		Runnable release = lease::close;
		if (accountReleases.putIfAbsent(name, release) != null) {
			lease.close();
			throw new IllegalArgumentException("Simulated player '" + name + "' already exists");
		}

		try {
			return create(PlayerOptions.builder().name(name)
					.authentication(AuthenticationMode.ONLINE)
					.accountId(lease.account().getAccountId()).build());
		} catch (RuntimeException | Error failure) {
			accountReleases.remove(name, release);
			lease.close();
			throw failure;
		}
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
		if (target == null)
			throw new ScenarioValidationException("Player '" + options.getName() + "' cannot connect to unknown process '"
					+ targetName + "'. Available: " + declarations.keySet());

		RunningProcess runningTarget = processes.get(targetName);
		String version = selectVersion(options, target);
		ProtocolSupport support = protocols.get(version);
		if (support == null)
			throw new ScenarioValidationException("Unsupported clientVersion '" + version + "'. Supported: "
					+ protocols.keySet());
		validateAuthentication(options, target, support);
		Runnable directRelease = reserveDirectly(options);

		ProtocolPlayer driven = null;
		SimulatedPlayer player;
		try {
			// Creation can fail after the account is marked in use, for example when a token refresh fails.
			driven = backend.create(PlayerRequest.builder()
					.name(options.getName())
					.clientVersion(version)
					.address(runningTarget.address())
					.authentication(options.getAuthentication())
					.accountId(options.getAccountId())
					.build());
			PlayerObservation observation = observations.create(driven);
			player = playerComposer.compose(
					driven,
					observation,
					options.getMetadata(),
					destroyed -> {
					players.remove(options.getName(), destroyed);
					Runnable release = accountReleases.remove(options.getName());
					if (release != null) release.run();
				}
			);
		} catch (RuntimeException | Error failure) {
			if (directRelease != null && accountReleases.remove(options.getName(), directRelease)) directRelease.run();
			if (driven != null) destroyAfterFailure(driven, failure);
			throw failure;
		}

		players.put(options.getName(), player);
		return player;
	}

	/**
	 * Reserves an online account selected by ID rather than through a lease, which already holds it.
	 *
	 * @return the reservation's release, or null when nothing was reserved here
	 */
	private @Nullable Runnable reserveDirectly(@NotNull PlayerOptions options) {
		if (options.getAuthentication() != AuthenticationMode.ONLINE) return null;
		if (accountReleases.containsKey(options.getName())) return null;

		String accountId = options.getAccountId();
		if (!reservations.reserve(accountId))
			throw new ScenarioValidationException("Authenticated account '" + accountId + "' is already in use");

		Runnable release = () -> reservations.release(accountId);
		accountReleases.put(options.getName(), release);

		return release;
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
	public synchronized void destroyAll() {
		Throwable failure = null;
		for (SimulatedPlayer player : all())
			try {
				player.destroy();
			} catch (RuntimeException | Error exception) {
				if (failure == null) {
					failure = exception;
					continue;
				}
				if (failure != exception)
					failure.addSuppressed(exception);
			}

		players.clear();
		accountReleases.values().forEach(Runnable::run);
		accountReleases.clear();
		if (failure instanceof Error error) throw error;
		if (failure != null) throw (RuntimeException) failure;
	}

	/**
	 * Prevents further creation and destroys every remaining player.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;

		closed = true;
		try {
			destroyAll();
		} finally {
			onClosed.accept(this);
		}
	}

	private static void destroyAfterFailure(@NotNull ProtocolPlayer driven, @NotNull Throwable failure) {
		try {
			driven.destroy();
		} catch (RuntimeException | Error cleanup) {
			if (cleanup != failure) failure.addSuppressed(cleanup);
		}
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

		if (version == null || version.isBlank()) {
			throw new ScenarioValidationException("Server '" + server.getName() + "' does not declare its native Minecraft version");
		}

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
		if (options.getAccountId() == null || options.getAccountId().isBlank())
			throw new ScenarioValidationException("Online player '" + options.getName() + "' requires an account ID");
		if (accounts.list().stream().noneMatch(account -> account.getAccountId().equals(options.getAccountId())))
			throw new ScenarioValidationException("Authenticated account '" + options.getAccountId() + "' is not available");
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
		if (closed)
			throw new IllegalStateException("Anvil scenario '" + scenario.getName() + "' is closed");
	}
}
