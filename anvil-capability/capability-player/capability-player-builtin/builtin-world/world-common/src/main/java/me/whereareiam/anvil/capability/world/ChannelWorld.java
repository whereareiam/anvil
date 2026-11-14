package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
import me.whereareiam.anvil.capability.world.model.LevelInfo;
import me.whereareiam.anvil.capability.world.model.ServerInfo;
import me.whereareiam.anvil.capability.world.model.WorldState;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * World capability driven over a player's typed channel: every dimension change the worker emits is kept in the
 * player's history, across reconnects, and the level of the last one, with the server of the last join, is the
 * player's until its connection ends. The world state is the last one the worker emitted, which is empty once the
 * connection ends; each wait on it offers every later state to its condition, so a state that holds only briefly still
 * satisfies it. The channel releases the event subscriptions together with the player.
 */
final class ChannelWorld implements World {
	private static final String DESCRIPTION = "enter a matching dimension";

	private final ProtocolChannel channel;
	private final CopyOnWriteArrayList<DimensionChange> changes = new CopyOnWriteArrayList<>();
	private final AtomicReference<LevelInfo> level = new AtomicReference<>();
	private final AtomicReference<ServerInfo> server = new AtomicReference<>();
	private final AtomicReference<WorldState> state = new AtomicReference<>(WorldState.EMPTY);
	private final CopyOnWriteArrayList<StateProbe> probes = new CopyOnWriteArrayList<>();

	ChannelWorld(@NotNull ProtocolChannel channel) {
		this.channel = channel;
		channel.subscribe(WorldChannel.SERVER, server::set);
		channel.subscribe(WorldChannel.DIMENSION, change -> {
			changes.add(change);
			level.set(change.getLevel());
		});
		channel.subscribe(WorldChannel.LEFT, left -> {
			level.getAndUpdate(now -> now != null && now.getDimension().equals(left) ? null : now);
			server.set(null);
		});
		channel.subscribe(WorldChannel.STATE, next -> {
			state.set(next);
			for (StateProbe probe : probes) probe.offer(next);
		});
	}

	@Override
	public @NotNull Optional<String> dimension() {
		return level().map(LevelInfo::getDimension);
	}

	@Override
	public @NotNull Optional<LevelInfo> level() {
		return Optional.ofNullable(level.get());
	}

	@Override
	public @NotNull Optional<ServerInfo> server() {
		return Optional.ofNullable(server.get());
	}

	@Override
	public @NotNull List<DimensionChange> changes() {
		return List.copyOf(changes);
	}

	@Override
	public @NotNull DimensionWatch watch() {
		return new Watch(new HistoryCursor<>(this::changes));
	}

	@Override
	public @NotNull WorldStateWatch states() {
		return new States();
	}

	/**
	 * Waits on the player's world state through probes that see every state the worker emits while they wait.
	 */
	private final class States implements WorldStateWatch {
		private static final String STATE_DESCRIPTION = "reach a matching world state";

		@Override
		public @NotNull WorldState current() {
			return state.get();
		}

		@Override
		public @NotNull WorldState until(@NotNull Predicate<WorldState> condition, @NotNull Duration timeout) {
			StateProbe probe = probe(condition);
			try {
				channel.await(() -> probe.found() != null, STATE_DESCRIPTION, timeout);
				return Objects.requireNonNull(probe.found());
			} finally {
				probes.remove(probe);
			}
		}

		@Override
		public void never(@NotNull Predicate<WorldState> condition, @NotNull Duration duration) {
			StateProbe probe = probe(condition);
			try {
				channel.await(() -> probe.found() != null, STATE_DESCRIPTION, duration);
			} catch (IllegalStateException quiet) {
				if (probe.found() == null) return;
			} finally {
				probes.remove(probe);
			}

			throw new IllegalStateException("Player's world reached a state it must not reach: " + probe.found());
		}

		@Override
		public @NotNull Expectation expect(@NotNull Predicate<WorldState> condition) {
			return Expectation.of(STATE_DESCRIPTION, () -> condition.test(state.get()));
		}

		/**
		 * Registers a probe and offers it the state now, after registering, so no state emitted meanwhile is missed.
		 */
		private StateProbe probe(Predicate<WorldState> condition) {
			StateProbe probe = new StateProbe(condition);
			probes.add(probe);
			probe.offer(state.get());
			return probe;
		}
	}

	/**
	 * Keeps the first state a wait's condition accepted.
	 */
	private static final class StateProbe {
		private final Predicate<WorldState> condition;
		private final AtomicReference<WorldState> found = new AtomicReference<>();

		private StateProbe(Predicate<WorldState> condition) {
			this.condition = condition;
		}

		private void offer(WorldState state) {
			if (found.get() == null && condition.test(state)) found.compareAndSet(null, state);
		}

		private WorldState found() {
			return found.get();
		}
	}

	/**
	 * Follows the player's dimension changes through a cursor and waits through the player's channel.
	 */
	private final class Watch implements DimensionWatch {
		private final HistoryCursor<DimensionChange> cursor;

		private Watch(HistoryCursor<DimensionChange> cursor) {
			this.cursor = cursor;
		}

		@Override
		public @NotNull DimensionChange changed(@NotNull Predicate<DimensionChange> matcher, @NotNull Duration timeout) {
			channel.await(() -> cursor.find(matcher) != null, DESCRIPTION, timeout);
			return Objects.requireNonNull(cursor.take(matcher));
		}

		@Override
		public void neverChanged(@NotNull Predicate<DimensionChange> matcher, @NotNull Duration duration) {
			try {
				channel.await(() -> cursor.find(matcher) != null, DESCRIPTION, duration);
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			DimensionChange change = Objects.requireNonNull(cursor.find(matcher));
			throw new IllegalStateException("Player made a dimension change it must not make: "
					+ (change.getFrom() == null ? "joined " : "from " + change.getFrom() + " to ") + change.getTo());
		}

		@Override
		public @NotNull Expectation expectChanged(@NotNull Predicate<DimensionChange> matcher) {
			return Expectation.of(DESCRIPTION, () -> cursor.find(matcher) != null);
		}

		@Override
		public @NotNull List<DimensionChange> unread() {
			return cursor.unread();
		}
	}
}
