package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.PlayerObservationFactory;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.player.account.AccountReservations;
import me.whereareiam.anvil.protocol.player.account.ProtocolAccountManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Selects a protocol library for each player, lazily creates one library instance per selected
 * library, and owns the scenario player managers of one engine.
 */
public final class DefaultPlayerService implements AutoCloseable {
	private final @NotNull ProtocolLibraryRegistry libraries;
	private final @NotNull EngineOptions options;
	private final @NotNull Path cacheDirectory;
	private final @NotNull Path accountsDirectory;
	private final @NotNull ProtocolArtifactResolver artifacts;
	private final @NotNull ProtocolLibrarySelector selector;
	private final @NotNull AccountManager accounts;
	private final List<RunningPlayerManager> managers = new CopyOnWriteArrayList<>();
	// One engine: pools and directly selected accounts of every scenario share these reservations.
	private final AccountReservations reservations = new AccountReservations();
	private final Map<String, List<ProtocolRelease>> releases = new ConcurrentHashMap<>();
	// Guarded by itself rather than this service, so player creation never waits behind closing managers.
	private final Map<String, ProtocolLibrary> created = new LinkedHashMap<>();
	private boolean librariesClosed;
	private boolean closed;

	/**
	 * Creates the player service for one engine without creating any protocol library.
	 *
	 * @param libraries installed protocol libraries
	 * @param options resolved engine options with cache and account directories
	 * @param artifacts verified artifact resolution shared by the engine
	 * @throws IllegalArgumentException when the cache or account directory is unresolved, or when the
	 * engine's library or a library with additional release data is not installed, or when additional
	 * release data is invalid
	 */
	public DefaultPlayerService(
			@NotNull ProtocolLibraryRegistry libraries,
			@NotNull EngineOptions options,
			@NotNull ProtocolArtifactResolver artifacts
	) {
		if (options.getCacheDirectory() == null || options.getAccountsDirectory() == null)
			throw new IllegalArgumentException("The player service requires resolved cache and account directories");
		if (options.getProtocolLibrary() != null) libraries.require(options.getProtocolLibrary());
		for (String library : options.getProtocolReleases().keySet()) libraries.require(library);

		this.libraries = libraries;
		this.options = options;
		this.cacheDirectory = options.getCacheDirectory();
		this.accountsDirectory = options.getAccountsDirectory();
		this.artifacts = artifacts;
		this.selector = new ProtocolLibrarySelector(libraries.ids(), this::releases, options.getProtocolLibrary(),
				options.getSupportPolicy(), System.err::println);
		this.accounts = new ProtocolAccountManager(libraries, accountsDirectory, reservations);
		// User-supplied release data is read now, so invalid files fail the engine instead of a later player.
		for (String library : options.getProtocolReleases().keySet()) releases(library);
	}

	/**
	 * Returns the engine-wide account manager aggregating every installed library's accounts.
	 *
	 * @return account manager
	 */
	public @NotNull AccountManager accounts() { return accounts; }

	/**
	 * Returns the libraries the scenario's players select when they declare none: the scenario's library,
	 * else the engine's, else, for the native version of each server, the one library ranked strongest
	 * whose release the effective support policy permits. Callers validate these libraries before any
	 * process starts and leave every other library to the first player that selects it.
	 *
	 * <p>Ranking reads the release data of every installed library, without holding this service's lock,
	 * also for a scenario that never creates a player.</p>
	 *
	 * @param scenario scenario validated by {@link #prepare(AnvilScenario)}
	 * @return distinct library identifiers, without creating any library
	 */
	public @NotNull List<String> scenarioLibraries(@NotNull AnvilScenario scenario) {
		synchronized (this) {
			ensureOpen();
		}

		return selector.scenarioLibraries(scenario);
	}

	/**
	 * Validates the scenario's library declaration before any process starts. A release that players would use by
	 * default for one of its servers but that cannot be launched, such as one without pinned checksums, prints a
	 * warning to standard error instead: a scenario that never creates such a player still runs, and creating one
	 * is refused.
	 *
	 * @param scenario scenario about to be prepared
	 * @throws ScenarioValidationException when the scenario names a library that is not installed
	 */
	public synchronized void prepare(@NotNull AnvilScenario scenario) {
		ensureOpen();
		selector.validate(scenario);
	}

	/**
	 * Opens the player manager of one prepared scenario.
	 *
	 * @param scenario prepared scenario
	 * @param processes scenario processes resolved at player creation
	 * @param observations scenario-bound player observations
	 * @param composer scenario-bound player composition
	 * @return scenario-owned player manager
	 */
	public synchronized @NotNull PlayerManager open(
			@NotNull AnvilScenario scenario,
			@NotNull ScenarioProcesses processes,
			@NotNull PlayerObservationFactory observations,
			@NotNull ProtocolPlayerComposer composer
	) {
		ensureOpen();
		RunningPlayerManager manager = new RunningPlayerManager(
				scenario,
				selector,
				this::library,
				processes,
				observations,
				composer,
				managers::remove,
				accounts,
				reservations
		);
		managers.add(manager);

		return manager;
	}

	/**
	 * Closes every scenario player manager, then every created library.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;

		closed = true;
		List<Throwable> failures = new ArrayList<>();
		for (PlayerManager manager : managers) attempt(manager::close, failures);

		managers.clear();
		List<ProtocolLibrary> closing;
		synchronized (created) {
			librariesClosed = true;
			closing = List.copyOf(created.values());
			created.clear();
		}

		for (ProtocolLibrary library : closing) attempt(library::close, failures);
		if (failures.isEmpty()) return;

		Throwable first = failures.getFirst();
		for (Throwable failure : failures.subList(1, failures.size()))
			if (failure != first) first.addSuppressed(failure);

		if (first instanceof Error error) throw error;
		throw (RuntimeException) first;
	}

	private @NotNull ProtocolLibrary library(@NotNull String id) {
		synchronized (created) {
			if (librariesClosed) throw new IllegalStateException("Cannot use the player service after it is closed");

			ProtocolLibrary library = created.get(id);
			if (library != null) return library;

			library = libraries.require(id).create(context(id));
			created.put(id, library);
			return library;
		}
	}

	private @NotNull List<ProtocolRelease> releases(@NotNull String id) {
		return releases.computeIfAbsent(id, key -> List.copyOf(libraries.require(key).releases(context(key))));
	}

	private ProtocolLibraryContext context(String id) {
		return ProtocolLibraryContext.builder()
				.cacheDirectory(cacheDirectory)
				.accountsDirectory(accountsDirectory)
				.artifacts(artifacts)
				.additionalReleases(options.getProtocolReleases().get(id))
				.build();
	}

	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Cannot use the player service after it is closed");
	}

	private void attempt(Runnable action, List<Throwable> failures) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failures.add(failure);
		}
	}
}
