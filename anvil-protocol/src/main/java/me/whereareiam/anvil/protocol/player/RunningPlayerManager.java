package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
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
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.model.GameConnection;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.player.PlayerObservationFactory;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.player.ProtocolLibrarySelector.Selection;
import me.whereareiam.anvil.protocol.player.account.AccountReservations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
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
	private final ProtocolLibrarySelector selector;
	private final Function<String, ProtocolLibrary> libraries;
	private final ScenarioProcesses processes;
	private final PlayerObservationFactory observations;
	private final Map<String, MinecraftProcess> declarations;
	private final Map<String, MinecraftServer> servers;
	private final ProtocolPlayerComposer playerComposer;
	private final Consumer<RunningPlayerManager> onClosed;
	private final AccountManager accounts;
	private final AccountReservations reservations;
	private final boolean sourceAddressesPreserved;

	private final Map<String, SimulatedPlayer> players = new ConcurrentHashMap<>();
	// Returns each online player's account to the engine: closes a claimed pool lease or unreserves a
	// directly selected account. Destruction runs them on the destroying thread without the manager lock,
	// so a destroy callback can never wait behind a create blocked on its library.
	private final Map<String, Runnable> accountReturns = new ConcurrentHashMap<>();
	private boolean closed;

	RunningPlayerManager(
			@NotNull AnvilScenario scenario,
			@NotNull ProtocolLibrarySelector selector,
			@NotNull Function<String, ProtocolLibrary> libraries,
			@NotNull ScenarioProcesses processes,
			@NotNull PlayerObservationFactory observations,
			@NotNull ProtocolPlayerComposer playerComposer,
			@NotNull Consumer<RunningPlayerManager> onClosed,
			@NotNull AccountManager accounts,
			@NotNull AccountReservations reservations,
			boolean sourceAddressesPreserved
	) {
		this.scenario = scenario;
		this.selector = selector;
		this.libraries = libraries;
		this.processes = processes;
		this.observations = observations;
		this.playerComposer = playerComposer;
		this.onClosed = onClosed;
		this.accounts = accounts;
		this.reservations = reservations;
		this.sourceAddressesPreserved = sourceAddressesPreserved;
		this.declarations = declarations(scenario);
		this.servers = scenario.getServers().stream()
				.collect(Collectors.toUnmodifiableMap(MinecraftServer::getName, Function.identity()));
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

	/**
	 * Creates a player signed in with a leased account. The leased account belongs to one protocol
	 * library, so the player selects that library, whatever the options, scenario or engine declare; any other
	 * library would sign it in with a different account stored under the same ID.
	 *
	 * @param options player options with a leased login
	 * @param lease unclaimed lease whose account the player signs in with
	 * @return created player, which returns the lease when it is destroyed
	 * @throws ScenarioValidationException when the player cannot be created, for example because the leased
	 * account's library has no permitted, launchable release for the player's Minecraft version; the lease is
	 * returned
	 */
	@Override
	public synchronized @NotNull SimulatedPlayer create(@NotNull PlayerOptions options, @NotNull AccountLease lease) {
		String name = options.getName();
		PlayerLogin login = options.getLogin();
		if (!login.isLeased())
			throw new IllegalArgumentException("Player '" + name + "' declares a " + login.getAuthentication()
					+ " login that takes no lease; declare PlayerLogin.leased(AuthenticationMode) to sign in with a leased account");
		if (!lease.claim())
			throw new IllegalStateException("Account lease for '" + lease.account().getAccountId() + "' is no longer available");

		// Recorded before the player exists, so a player destroyed immediately still returns its lease.
		Runnable returnLease = lease::close;
		if (accountReturns.putIfAbsent(name, returnLease) != null) {
			lease.close();
			throw new IllegalArgumentException("Simulated player '" + name + "' already exists");
		}

		try {
			return create(options.toBuilder()
					.protocolLibrary(lease.account().getLibraryId())
					.login(PlayerLogin.account(login.getAuthentication(), lease.account().getAccountId())).build());
		} catch (RuntimeException | Error failure) {
			accountReturns.remove(name, returnLease);
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

		PlayerConnection connection = options.getConnection();
		String targetName = connection.getTarget() == null ? scenario.getEntrypoint() : connection.getTarget();
		MinecraftProcess target = declarations.get(targetName);
		if (target == null)
			throw new ScenarioValidationException("Player '" + options.getName() + "' cannot connect to unknown process '"
					+ targetName + "'. Available: " + declarations.keySet());
		if (options.getLogin().isLeased())
			throw new ScenarioValidationException("Player '" + options.getName() + "' declares a leased login; create it "
					+ "with PlayerManager.create(PlayerOptions, AccountLease)");

		RunningProcess runningTarget = processes.get(targetName);
		MinecraftVersion version = selectVersion(options, target);
		Selection selection = selector.select(options, scenario, version);
		validateAuthentication(options, target, selection, version);
		InetAddress sourceAddress = sourceAddress(options, runningTarget);
		ProtocolLibrary library = libraries.apply(selection.library());
		Runnable directReturn = reserveDirectly(options);

		ProtocolPlayer driven = null;
		SimulatedPlayer player;
		try {
			// Creation can fail after the account is marked in use, for example when a token refresh fails.
			driven = library.create(PlayerRequest.builder()
					.name(options.getName())
					.login(options.getLogin())
					.clientVersion(version)
					.connection(GameConnection.builder()
							.address(runningTarget.address())
							.virtualHost(connection.getVirtualHost())
							.sourceAddress(sourceAddress)
							.build())
					.build());
			PlayerObservation observation = observations.create(driven, targetName);
			player = playerComposer.compose(
					driven,
					observation,
					options.getMetadata(),
					destroyed -> {
					players.remove(options.getName(), destroyed);
					Runnable accountReturn = accountReturns.remove(options.getName());
					if (accountReturn != null) accountReturn.run();
				}
			);
		} catch (RuntimeException | Error failure) {
			if (directReturn != null && accountReturns.remove(options.getName(), directReturn)) directReturn.run();
			if (driven != null) destroyAfterFailure(driven, failure);
			throw failure;
		}

		players.put(options.getName(), player);
		return player;
	}

	/**
	 * Reserves an online account selected by ID rather than through a lease, which already holds it.
	 *
	 * @return the action that unreserves the account, or null when nothing was reserved here
	 */
	private @Nullable Runnable reserveDirectly(@NotNull PlayerOptions options) {
		String accountId = options.getLogin().getAccountId();
		if (accountId == null) return null;
		if (accountReturns.containsKey(options.getName())) return null;

		if (!reservations.reserve(accountId))
			throw new ScenarioValidationException("Authenticated account '" + accountId + "' is already in use");

		Runnable unreserve = () -> reservations.unreserve(accountId);
		accountReturns.put(options.getName(), unreserve);

		return unreserve;
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
		accountReturns.values().forEach(Runnable::run);
		accountReturns.clear();
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

	private MinecraftVersion selectVersion(PlayerOptions options, MinecraftProcess target) {
		Set<MinecraftVersion> nativeVersions = reachableServers(target).stream()
				.map(this::nativeVersion)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (nativeVersions.isEmpty())
			throw new ScenarioValidationException("Connection target '" + target.getName() + "' reaches no Minecraft servers");
		if (nativeVersions.size() > 1)
			throw new ScenarioValidationException("Connection target '" + target.getName()
					+ "' reaches servers with different native versions " + nativeVersions
					+ "; no exact-fidelity client can traverse all of them");

		MinecraftVersion compatible = nativeVersions.iterator().next();
		if (options.getClientVersion() == null)
			return compatible;
		if (!compatible.equals(clientVersion(options)))
			throw new ScenarioValidationException("Native client version '" + options.getClientVersion()
					+ "' does not match servers reachable through '" + target.getName()
					+ "' using version '" + compatible + "'");
		return compatible;
	}

	private Collection<MinecraftServer> reachableServers(MinecraftProcess target) {
		if (target instanceof MinecraftServer server)
			return List.of(server);
		MinecraftProxy proxy = (MinecraftProxy) target;
		return proxy.getServers().stream().map(servers::get).toList();
	}

	private MinecraftVersion nativeVersion(MinecraftServer server) {
		MinecraftVersion version;
		try {
			version = server.nativeVersion();
		} catch (IllegalArgumentException invalid) {
			throw new ScenarioValidationException("Server '" + server.getName()
					+ "' declares an invalid native Minecraft version: " + invalid.getMessage(), invalid);
		}

		if (version == null)
			throw new ScenarioValidationException("Server '" + server.getName() + "' does not declare its native Minecraft version");

		return version;
	}

	private MinecraftVersion clientVersion(PlayerOptions options) {
		try {
			return MinecraftVersion.parse(options.getClientVersion());
		} catch (IllegalArgumentException invalid) {
			throw new ScenarioValidationException("Player '" + options.getName() + "' declares an invalid client version: "
					+ invalid.getMessage(), invalid);
		}
	}

	/**
	 * A session identity is only verifiable by the session server it names, so the process the player joins
	 * must verify against that same server; Mojang would refuse it.
	 */
	private void validateSessionIdentity(PlayerOptions options, SessionIdentity identity, MinecraftProcess target) {
		URI sessionServer = identity.getSessionServer();
		if (!sessionServer.equals(target.getSessionServer()))
			throw new ScenarioValidationException("Player '" + options.getName() + "' is verified by session server "
					+ sessionServer + ", but process '" + target.getName() + "' verifies logins against "
					+ (target.getSessionServer() == null ? "Mojang" : target.getSessionServer())
					+ "; declare the same sessionServer on the process");
	}

	private void validateAuthentication(
			PlayerOptions options,
			MinecraftProcess target,
			Selection selection,
			MinecraftVersion version
	) {
		PlayerLogin login = options.getLogin();
		if (login.getAuthentication() == AuthenticationMode.OFFLINE && target.isOnlineMode())
			throw new ScenarioValidationException("Offline player '" + options.getName()
					+ "' cannot join online-mode process '" + target.getName() + "'");
		if (!login.getAuthentication().usesAccount()) return;
		if (login.getAuthentication() == AuthenticationMode.ONLINE && !target.isOnlineMode())
			throw new ScenarioValidationException("Online player '" + options.getName()
					+ "' requires an online-mode entrypoint; use AuthenticationMode.ON_REQUEST when a plugin of an "
					+ "offline-mode entrypoint requests authentication itself");
		if (login.getSessionIdentity() != null) {
			validateSessionIdentity(options, login.getSessionIdentity(), target);
			return;
		}

		// Account IDs are library-local: the player needs the account its selected library stores under that ID.
		String accountId = login.getAccountId();
		List<String> owners = accounts.list().stream()
				.filter(candidate -> candidate.getAccountId().equals(accountId))
				.map(AuthenticationAccount::getLibraryId)
				.toList();
		if (owners.isEmpty())
			throw new ScenarioValidationException("Authenticated account '" + accountId + "' is not available");
		if (!owners.contains(selection.library()))
			throw new ScenarioValidationException("Authenticated account '" + accountId
					+ "' is not stored by protocol library '" + selection.library() + "' selected for player '"
					+ options.getName() + "'; it is stored by " + owners);
		if (!selection.release().getFeatures().contains(ProtocolFeature.ONLINE_AUTHENTICATION))
			throw new ScenarioValidationException("Protocol library '" + selection.library()
					+ "' does not support online authentication for client version '" + version + "'");
	}

	/**
	 * Resolves the source address only when the joined process will see it: the scenario's execution must hand
	 * connections over unchanged, the target must listen on loopback in the same address family, and this machine
	 * must be able to bind the address. Any other case is refused rather than connecting from another address.
	 */
	private @Nullable InetAddress sourceAddress(PlayerOptions options, RunningProcess target) {
		String declared = options.getConnection().getSourceAddress();
		if (declared == null) return null;

		String player = "Player '" + options.getName() + "' declares the source address " + declared;
		if (!sourceAddressesPreserved)
			throw new ScenarioValidationException(player + ", but the scenario's execution provider translates game "
					+ "connections, so process '" + target.name() + "' would see another address; Docker execution, for "
					+ "example, forwards published ports through its own network. Use local execution or declare no source address");

		InetAddress address = parseLiteral(declared);
		InetAddress listener = target.address().getAddress();
		if (listener == null || !listener.isLoopbackAddress() || listener.getClass() != address.getClass())
			throw new ScenarioValidationException(player + ", but process '" + target.name() + "' listens on "
					+ target.address() + "; a loopback source address only reaches a loopback listener of the same address family");

		try (Socket socket = new Socket()) {
			socket.bind(new InetSocketAddress(address, 0));
		} catch (IOException unavailable) {
			throw new ScenarioValidationException(player + ", which this machine cannot bind (" + unavailable.getMessage()
					+ "). Linux routes all of 127.0.0.0/8 to loopback; macOS configures only 127.0.0.1 unless an alias "
					+ "is added, for example with 'sudo ifconfig lo0 alias " + declared + "'", unavailable);
		}

		return address;
	}

	private static InetAddress parseLiteral(String literal) {
		try {
			// The connection accepted only an address literal, so this never looks a name up.
			return InetAddress.getByName(literal);
		} catch (UnknownHostException invalid) {
			throw new IllegalStateException("Validated source address '" + literal + "' is not an address literal", invalid);
		}
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
