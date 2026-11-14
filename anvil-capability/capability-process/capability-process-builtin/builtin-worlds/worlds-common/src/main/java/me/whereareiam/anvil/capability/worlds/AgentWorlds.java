package me.whereareiam.anvil.capability.worlds;

import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.exception.ChannelUnavailableException;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.worlds.agent.WorldsOperations;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLogRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacements;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentTransfer;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChange;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChanges;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldCreation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldEvent;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldEvents;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldList;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldLoad;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldOutcome;
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldChange;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.model.WorldEvent;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import me.whereareiam.anvil.capability.worlds.type.WorldEventType;
import me.whereareiam.anvil.capability.worlds.type.WorldKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Worlds capability of one server, over the operations its worlds agent extension answers. The world changes and world
 * events the server reports are read from the agent as they are needed and kept, so a restarted server's earlier ones
 * stay, followed by those of its new process.
 *
 * <p>A simulated player is matched by the UUIDs of every profile its client was granted, read on every call, so a
 * profile granted at a later login counts as well.</p>
 *
 * <p>The worlds a scenario created are deleted when it passes, if the server still has them loaded, so a server kept
 * for later scenarios offers each the worlds it started with. The server refuses to delete a world a player is still
 * in, and the scenario's players have only just left when it finishes, so a refused deletion is retried for a while
 * before it fails. After a failed scenario the worlds stay for diagnostics.</p>
 */
final class AgentWorlds implements Worlds {
	private static final Duration POLL_INTERVAL = Duration.ofMillis(25);
	private static final Duration CLEANUP_TIMEOUT = Duration.ofSeconds(10);
	private static final ChannelOperation<Void, AgentWorldList> WORLDS = channel(WorldsOperations.WORLDS);
	private static final ChannelOperation<Void, AgentPlacements> PLACEMENTS = channel(WorldsOperations.PLACEMENTS);
	private static final ChannelOperation<AgentTransfer, Boolean> SEND = channel(WorldsOperations.SEND);
	private static final ChannelOperation<AgentLogRequest, AgentWorldChanges> CHANGES =
			channel(WorldsOperations.CHANGES);
	private static final ChannelOperation<AgentWorldCreation, AgentWorldOutcome> CREATE = channel(WorldsOperations.CREATE);
	private static final ChannelOperation<AgentWorldLoad, AgentWorldOutcome> LOAD = channel(WorldsOperations.LOAD);
	private static final ChannelOperation<String, AgentWorldOutcome> UNLOAD = channel(WorldsOperations.UNLOAD);
	private static final ChannelOperation<String, AgentWorldOutcome> DELETE = channel(WorldsOperations.DELETE);
	private static final ChannelOperation<AgentLogRequest, AgentWorldEvents> EVENTS = channel(WorldsOperations.EVENTS);

	private final @NotNull String process;
	private final @NotNull RequestChannel channel;
	private final @NotNull Duration cleanupTimeout;
	/**
	 * Keys of the worlds the current scenario created and has not deleted or unloaded itself.
	 */
	private final Set<String> created = new LinkedHashSet<>();
	private final List<WorldChange> changes = new ArrayList<>();
	private final List<WorldEvent> events = new ArrayList<>();
	private @Nullable String log;
	private long read;
	private @Nullable String eventLog;
	private long eventsRead;

	AgentWorlds(@NotNull String process, @NotNull RequestChannel channel) {
		this(process, channel, CLEANUP_TIMEOUT);
	}

	AgentWorlds(@NotNull String process, @NotNull RequestChannel channel, @NotNull Duration cleanupTimeout) {
		this.process = process;
		this.channel = channel;
		this.cleanupTimeout = cleanupTimeout;
	}

	@Override
	public @NotNull List<ServerWorld> worlds() {
		return Objects.requireNonNull(channel.request(WORLDS, null)).getWorlds().stream()
				.map(AgentWorlds::world)
				.toList();
	}

	@Override
	public @NotNull Optional<ServerWorld> world(@NotNull String key) {
		String world = key(key);

		return worlds().stream().filter(loaded -> loaded.getKey().equals(world)).findFirst();
	}

	@Override
	public @NotNull Optional<WorldLocation> location(@NotNull SimulatedPlayer player) {
		return placement(player).map(placement -> location(placement.getLocation()));
	}

	@Override
	public void send(@NotNull SimulatedPlayer player, @NotNull String world) {
		send(player, loaded(key(world)).getSpawn());
	}

	@Override
	public void send(@NotNull SimulatedPlayer player, @NotNull WorldLocation location) {
		WorldLocation target = location.toBuilder().world(key(location.getWorld())).build();
		loaded(target.getWorld());
		AgentPlacement placement = placement(player)
				.orElseThrow(() -> new IllegalStateException("Player '" + player.name() + "' is not on '" + process + "'"));

		AgentTransfer transfer = AgentTransfer.builder()
				.uniqueId(placement.getUniqueId())
				.location(AgentLocation.builder()
						.world(target.getWorld())
						.x(target.getX())
						.y(target.getY())
						.z(target.getZ())
						.yaw(target.getYaw())
						.pitch(target.getPitch())
						.build())
				.build();
		if (!Boolean.TRUE.equals(channel.request(SEND, transfer)))
			throw new IllegalStateException("'" + process + "' refused to move player '" + player.name() + "' to '"
					+ target.getWorld() + "'");
	}

	@Override
	public @NotNull ServerWorld create(@NotNull WorldCreation creation) {
		String key = key(creation.getKey());
		absent(key, creation.getKind());

		AgentWorldOutcome outcome = Objects.requireNonNull(channel.request(CREATE, AgentWorldCreation.builder()
				.key(key)
				.kind(creation.getKind().name())
				.seed(creation.getSeed())
				.generator(creation.getGenerator().name())
				.build()));
		done(outcome, "create the world '" + key + "'");
		synchronized (created) {
			created.add(key);
		}

		return world(Objects.requireNonNull(outcome.getWorld()));
	}

	@Override
	public @NotNull ServerWorld load(@NotNull String world, @NotNull WorldKind kind) {
		String key = key(world);
		absent(key, kind);

		AgentWorldOutcome outcome = Objects.requireNonNull(channel.request(LOAD, AgentWorldLoad.builder()
				.key(key)
				.kind(kind.name())
				.build()));
		done(outcome, "load the world '" + key + "'");

		return world(Objects.requireNonNull(outcome.getWorld()));
	}

	@Override
	public void unload(@NotNull String world) {
		String key = key(world);
		loaded(key);

		done(Objects.requireNonNull(channel.request(UNLOAD, key)), "unload the world '" + key + "'");
		forget(key);
	}

	@Override
	public void delete(@NotNull String world) {
		String key = key(world);
		loaded(key);

		done(Objects.requireNonNull(channel.request(DELETE, key)), "delete the world '" + key + "'");
		forget(key);
	}

	/**
	 * Deletes the worlds the finished scenario created and the server still has loaded, when the scenario passed, and
	 * forgets them either way. A server that has stopped has nothing left to delete.
	 *
	 * @param passed whether the scenario passed
	 * @throws IllegalStateException when the server kept refusing to delete a world, with later failures suppressed
	 */
	void finishScenario(boolean passed) {
		List<String> keys;
		synchronized (created) {
			keys = List.copyOf(created);
			created.clear();
		}
		if (!passed || keys.isEmpty()) return;

		try {
			Set<String> loaded = worlds().stream().map(ServerWorld::getKey).collect(Collectors.toSet());
			RuntimeException failure = null;
			for (String key : keys) {
				if (!loaded.contains(key)) continue;

				try {
					deleteCreated(key);
				} catch (ChannelUnavailableException stopped) {
					throw stopped;
				} catch (RuntimeException refused) {
					if (failure == null) failure = refused;
					else failure.addSuppressed(refused);
				}
			}
			if (failure != null) throw failure;
		} catch (ChannelUnavailableException stopped) {
			// The server stopped, and its worlds went with it.
		}
	}

	/**
	 * Deletes a world the scenario created, asking again while the server refuses, as it does while the players who
	 * have just left are still in it.
	 */
	private void deleteCreated(@NotNull String key) {
		long deadline = System.nanoTime() + cleanupTimeout.toNanos();
		while (true) {
			AgentWorldOutcome outcome = Objects.requireNonNull(channel.request(DELETE, key));
			if (outcome.getRefusal() == null || System.nanoTime() >= deadline) {
				done(outcome, "delete the world '" + key + "' its scenario created");
				return;
			}

			sleep();
		}
	}

	private void forget(@NotNull String key) {
		synchronized (created) {
			created.remove(key);
		}
	}

	@Override
	public @NotNull WorldChangeWatch watch() {
		return new Watch(new HistoryCursor<>(this::changes));
	}

	@Override
	public @NotNull WorldEventWatch watchWorlds() {
		return new EventWatch(new HistoryCursor<>(this::events));
	}

	/**
	 * Reads the changes the agent reported since the last read, and returns every change kept so far. A log the agent
	 * no longer keeps, because the server restarted, is followed by the new process's log from its start.
	 */
	private synchronized @NotNull List<WorldChange> changes() {
		AgentWorldChanges unread = Objects.requireNonNull(channel.request(CHANGES, AgentLogRequest.builder()
				.log(log)
				.after(read)
				.build()));
		if (!unread.getLog().equals(log)) read = 0;
		log = unread.getLog();
		for (AgentWorldChange change : unread.getChanges()) {
			changes.add(new WorldChange(new PlayerProfile(change.getUsername(), change.getUniqueId()), change.getFrom(),
					change.getTo(), Instant.ofEpochMilli(change.getAt())));
			read = change.getPosition();
		}

		return List.copyOf(changes);
	}

	/**
	 * Reads the world events the agent reported since the last read, and returns every event kept so far, as
	 * {@link #changes()} does for world changes.
	 */
	private synchronized @NotNull List<WorldEvent> events() {
		AgentWorldEvents unread = Objects.requireNonNull(channel.request(EVENTS, AgentLogRequest.builder()
				.log(eventLog)
				.after(eventsRead)
				.build()));
		if (!unread.getLog().equals(eventLog)) eventsRead = 0;
		eventLog = unread.getLog();
		for (AgentWorldEvent event : unread.getEvents()) {
			events.add(new WorldEvent(event.getWorld(), WorldEventType.valueOf(event.getType()),
					Instant.ofEpochMilli(event.getAt())));
			eventsRead = event.getPosition();
		}

		return List.copyOf(events);
	}

	/**
	 * Checks that a world to create or load is of a kind a server makes and is not loaded yet.
	 */
	private void absent(@NotNull String key, @NotNull WorldKind kind) {
		if (kind == WorldKind.CUSTOM)
			throw new IllegalArgumentException("A server creates and loads OVERWORLD, NETHER or END worlds, not CUSTOM ones");
		if (world(key).isPresent()) throw new IllegalArgumentException("'" + process + "' has already loaded the world '" + key + "'");
	}

	/**
	 * Fails with what kept the server from doing a request: a world it does not have or already has, a request the
	 * platform cannot do, or one the server refused.
	 */
	private void done(@NotNull AgentWorldOutcome outcome, @NotNull String request) {
		if (outcome.getMissing() != null)
			throw new IllegalArgumentException("'" + process + "' cannot " + request + ": " + outcome.getMissing());
		if (outcome.getUnsupported() != null)
			throw new UnsupportedOperationException("'" + process + "' cannot " + request + ": " + outcome.getUnsupported());
		if (outcome.getRefusal() != null)
			throw new IllegalStateException("'" + process + "' refused to " + request + ": " + outcome.getRefusal());
	}

	private @NotNull ServerWorld loaded(@NotNull String key) {
		List<ServerWorld> worlds = worlds();

		return worlds.stream()
				.filter(world -> world.getKey().equals(key))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("'" + process + "' has not loaded the world '" + key
						+ "'; it has " + worlds.stream().map(ServerWorld::getKey).toList()));
	}

	private @NotNull Optional<AgentPlacement> placement(@NotNull SimulatedPlayer player) {
		Set<UUID> uniqueIds = uniqueIds(player);

		return Objects.requireNonNull(channel.request(PLACEMENTS, null)).getPlacements().stream()
				.filter(placement -> uniqueIds.contains(placement.getUniqueId()))
				.findFirst();
	}

	/**
	 * Names a world by its level's key, putting a key without a namespace in the {@code minecraft} namespace.
	 */
	private static @NotNull String key(@NotNull String world) {
		return world.indexOf(':') < 0 ? "minecraft:" + world : world;
	}

	private static @NotNull ServerWorld world(@NotNull AgentWorld world) {
		return ServerWorld.builder()
				.key(world.getKey())
				.kind(WorldKind.valueOf(world.getKind()))
				.spawn(location(world.getSpawn()))
				.build();
	}

	private static @NotNull WorldLocation location(@NotNull AgentLocation location) {
		return WorldLocation.builder()
				.world(location.getWorld())
				.x(location.getX())
				.y(location.getY())
				.z(location.getZ())
				.yaw(location.getYaw())
				.pitch(location.getPitch())
				.build();
	}

	private static @NotNull Set<UUID> uniqueIds(@NotNull SimulatedPlayer player) {
		return player.grantedProfiles().stream()
				.map(PlayerProfile::getUniqueId)
				.collect(Collectors.toSet());
	}

	private static <Q, R> @NotNull ChannelOperation<Q, R> channel(@NotNull AgentOperation<Q, R> operation) {
		return new ChannelOperation<>(operation.getName(), operation.getRequestType(), operation.getResponseType());
	}

	/**
	 * Checks a condition until it holds, failing with the supplied message once the timeout has passed.
	 */
	private void poll(@NotNull BooleanSupplier condition, @NotNull Duration timeout, @NotNull String failure) {
		if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");

		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) return;

			sleep();
		}
		if (condition.getAsBoolean()) return;

		throw new IllegalStateException(failure + " within " + timeout);
	}

	private void sleep() {
		try {
			Thread.sleep(POLL_INTERVAL.toMillis());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while watching the worlds of '" + process + "'", exception);
		}
	}

	/**
	 * A watch over the world changes the server reports.
	 */
	private final class Watch implements WorldChangeWatch {
		private final HistoryCursor<WorldChange> cursor;

		private Watch(@NotNull HistoryCursor<WorldChange> cursor) {
			this.cursor = cursor;
		}

		@Override
		public @NotNull WorldChange changed(@NotNull SimulatedPlayer player, @NotNull String world, @NotNull Duration timeout) {
			String key = key(world);
			return next(of(player).and(change -> change.getTo().equals(key)), timeout,
					"'" + process + "' did not move player '" + player.name() + "' to '" + key + "'");
		}

		@Override
		public @NotNull WorldChange changed(@NotNull SimulatedPlayer player, @NotNull Duration timeout) {
			return next(of(player), timeout, "'" + process + "' did not move player '" + player.name() + "' to another world");
		}

		@Override
		public void neverChanged(@NotNull SimulatedPlayer player, @NotNull Duration duration) {
			Predicate<WorldChange> matcher = of(player);
			try {
				poll(() -> cursor.find(matcher) != null, duration, "moved");
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			WorldChange change = Objects.requireNonNull(cursor.find(matcher));
			throw new IllegalStateException("'" + process + "' moved player '" + player.name() + "' from '"
					+ change.getFrom() + "' to '" + change.getTo() + "', which it must not do");
		}

		@Override
		public @NotNull Expectation expectChanged(@NotNull SimulatedPlayer player, @NotNull String world) {
			String key = key(world);
			Predicate<WorldChange> matcher = of(player).and(change -> change.getTo().equals(key));

			return Expectation.of("be moved to '" + key + "' by '" + process + "'", () -> cursor.find(matcher) != null);
		}

		@Override
		public @NotNull List<WorldChange> unread() {
			return cursor.unread();
		}

		private @NotNull WorldChange next(@NotNull Predicate<WorldChange> matcher, @NotNull Duration timeout,
				@NotNull String failure) {
			poll(() -> cursor.find(matcher) != null, timeout, failure);
			return Objects.requireNonNull(cursor.take(matcher));
		}

		private @NotNull Predicate<WorldChange> of(@NotNull SimulatedPlayer player) {
			return change -> uniqueIds(player).contains(change.getProfile().getUniqueId());
		}
	}

	/**
	 * A watch over the worlds the server loads and unloads.
	 */
	private final class EventWatch implements WorldEventWatch {
		private final HistoryCursor<WorldEvent> cursor;

		private EventWatch(@NotNull HistoryCursor<WorldEvent> cursor) {
			this.cursor = cursor;
		}

		@Override
		public @NotNull WorldEvent loaded(@NotNull String world, @NotNull Duration timeout) {
			String key = key(world);
			return next(of(key, WorldEventType.LOADED), timeout, "'" + process + "' did not load the world '" + key + "'");
		}

		@Override
		public @NotNull WorldEvent unloaded(@NotNull String world, @NotNull Duration timeout) {
			String key = key(world);
			return next(of(key, WorldEventType.UNLOADED), timeout, "'" + process + "' did not unload the world '" + key + "'");
		}

		@Override
		public void neverLoaded(@NotNull String world, @NotNull Duration duration) {
			String key = key(world);
			Predicate<WorldEvent> matcher = of(key, WorldEventType.LOADED);
			try {
				poll(() -> cursor.find(matcher) != null, duration, "loaded");
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			throw new IllegalStateException("'" + process + "' loaded the world '" + key + "', which it must not do");
		}

		@Override
		public @NotNull Expectation expectLoaded(@NotNull String world) {
			String key = key(world);
			Predicate<WorldEvent> matcher = of(key, WorldEventType.LOADED);

			return Expectation.of("see '" + process + "' load the world '" + key + "'", () -> cursor.find(matcher) != null);
		}

		@Override
		public @NotNull List<WorldEvent> unread() {
			return cursor.unread();
		}

		private @NotNull WorldEvent next(@NotNull Predicate<WorldEvent> matcher, @NotNull Duration timeout,
				@NotNull String failure) {
			poll(() -> cursor.find(matcher) != null, timeout, failure);
			return Objects.requireNonNull(cursor.take(matcher));
		}

		private @NotNull Predicate<WorldEvent> of(@NotNull String key, @NotNull WorldEventType type) {
			return event -> event.getType() == type && event.getWorld().equals(key);
		}
	}
}
