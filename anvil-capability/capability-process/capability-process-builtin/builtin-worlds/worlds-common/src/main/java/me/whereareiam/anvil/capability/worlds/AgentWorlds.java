package me.whereareiam.anvil.capability.worlds;

import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.worlds.agent.WorldsOperations;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentLocation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacement;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacements;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentTransfer;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorld;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChange;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChanges;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChangesRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldCreation;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldList;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldOutcome;
import me.whereareiam.anvil.capability.worlds.model.ServerWorld;
import me.whereareiam.anvil.capability.worlds.model.WorldChange;
import me.whereareiam.anvil.capability.worlds.model.WorldCreation;
import me.whereareiam.anvil.capability.worlds.model.WorldLocation;
import me.whereareiam.anvil.capability.worlds.type.WorldKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Worlds capability of one server, over the operations its worlds agent extension answers. The world changes the
 * server reports are read from the agent as they are needed and kept, so a restarted server's earlier changes stay,
 * followed by those of its new process.
 *
 * <p>A simulated player is matched by the UUIDs of every profile its client was granted, read on every call, so a
 * profile granted at a later login counts as well.</p>
 */
final class AgentWorlds implements Worlds {
	private static final Duration POLL_INTERVAL = Duration.ofMillis(25);
	private static final ChannelOperation<Void, AgentWorldList> WORLDS = channel(WorldsOperations.WORLDS);
	private static final ChannelOperation<Void, AgentPlacements> PLACEMENTS = channel(WorldsOperations.PLACEMENTS);
	private static final ChannelOperation<AgentTransfer, Boolean> SEND = channel(WorldsOperations.SEND);
	private static final ChannelOperation<AgentWorldChangesRequest, AgentWorldChanges> CHANGES =
			channel(WorldsOperations.CHANGES);
	private static final ChannelOperation<AgentWorldCreation, AgentWorldOutcome> CREATE = channel(WorldsOperations.CREATE);
	private static final ChannelOperation<String, AgentWorldOutcome> UNLOAD = channel(WorldsOperations.UNLOAD);

	private final @NotNull String process;
	private final @NotNull RequestChannel channel;
	private final List<WorldChange> changes = new ArrayList<>();
	private @Nullable String log;
	private long read;

	AgentWorlds(@NotNull String process, @NotNull RequestChannel channel) {
		this.process = process;
		this.channel = channel;
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
		if (creation.getKind() == WorldKind.CUSTOM)
			throw new IllegalArgumentException("A server creates an OVERWORLD, NETHER or END world, not a CUSTOM one");
		if (world(key).isPresent()) throw new IllegalArgumentException("'" + process + "' has already loaded the world '" + key + "'");

		AgentWorldOutcome outcome = Objects.requireNonNull(channel.request(CREATE, AgentWorldCreation.builder()
				.key(key)
				.kind(creation.getKind().name())
				.seed(creation.getSeed())
				.generator(creation.getGenerator().name())
				.build()));
		done(outcome, "create the world '" + key + "'");

		return world(Objects.requireNonNull(outcome.getWorld()));
	}

	@Override
	public void unload(@NotNull String world) {
		String key = key(world);
		loaded(key);

		done(Objects.requireNonNull(channel.request(UNLOAD, key)), "unload the world '" + key + "'");
	}

	@Override
	public @NotNull WorldChangeWatch watch() {
		return new Watch(new HistoryCursor<>(this::changes));
	}

	/**
	 * Reads the changes the agent reported since the last read, and returns every change kept so far. A log the agent
	 * no longer keeps, because the server restarted, is followed by the new process's log from its start.
	 */
	private synchronized @NotNull List<WorldChange> changes() {
		AgentWorldChanges unread = Objects.requireNonNull(channel.request(CHANGES, AgentWorldChangesRequest.builder()
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
	 * Fails with what kept the server from doing a request, either because the platform cannot or because it refused.
	 */
	private void done(@NotNull AgentWorldOutcome outcome, @NotNull String request) {
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
}
