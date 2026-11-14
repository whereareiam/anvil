package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.DimensionChange;
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
 * player's history, across reconnects, and the dimension of the last one is the player's until its connection ends.
 * The channel releases the event subscriptions together with the player.
 */
final class ChannelWorld implements World {
	private static final String DESCRIPTION = "enter a matching dimension";

	private final ProtocolChannel channel;
	private final CopyOnWriteArrayList<DimensionChange> changes = new CopyOnWriteArrayList<>();
	private final AtomicReference<String> dimension = new AtomicReference<>();

	ChannelWorld(@NotNull ProtocolChannel channel) {
		this.channel = channel;
		channel.subscribe(WorldChannel.DIMENSION, change -> {
			changes.add(change);
			dimension.set(change.getTo());
		});
		channel.subscribe(WorldChannel.LEFT, left -> dimension.compareAndSet(left, null));
	}

	@Override
	public @NotNull Optional<String> dimension() {
		return Optional.ofNullable(dimension.get());
	}

	@Override
	public @NotNull List<DimensionChange> changes() {
		return List.copyOf(changes);
	}

	@Override
	public @NotNull DimensionWatch watch() {
		return new Watch(new HistoryCursor<>(this::changes));
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
