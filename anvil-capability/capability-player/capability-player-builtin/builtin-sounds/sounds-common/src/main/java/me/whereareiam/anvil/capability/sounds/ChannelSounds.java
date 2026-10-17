package me.whereareiam.anvil.capability.sounds;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Sounds capability driven over a player's typed channel: every heard-sound event the worker emits is kept in the
 * player's history, across reconnects. The channel releases the event subscription together with the player.
 */
final class ChannelSounds implements Sounds {
	private final ProtocolChannel channel;
	private final CopyOnWriteArrayList<HeardSound> history = new CopyOnWriteArrayList<>();

	ChannelSounds(@NotNull ProtocolChannel channel) {
		this.channel = channel;
		channel.subscribe(SoundsOperations.HEARD, history::add);
	}

	@Override
	public @NotNull List<HeardSound> history() {
		return List.copyOf(history);
	}

	@Override
	public @NotNull SoundWatch watch() {
		return new Watch(new HistoryCursor<>(this::history));
	}

	/**
	 * A watch over this player's history, which waits through the player's channel.
	 */
	@RequiredArgsConstructor
	private final class Watch implements SoundWatch {
		private final HistoryCursor<HeardSound> cursor;

		@Override
		public @NotNull HeardSound heard(@NotNull Predicate<HeardSound> matcher, @NotNull Duration timeout) {
			channel.await(() -> cursor.find(matcher) != null, "hear a matching sound", timeout);
			return Objects.requireNonNull(cursor.take(matcher));
		}

		@Override
		public void neverHeard(@NotNull Predicate<HeardSound> matcher, @NotNull Duration duration) {
			try {
				channel.await(() -> cursor.find(matcher) != null, "hear a matching sound", duration);
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			throw new IllegalStateException("Player heard a sound it must not hear: "
					+ Objects.requireNonNull(cursor.find(matcher)).getKey());
		}

		@Override
		public @NotNull Expectation expectHeard(@NotNull Predicate<HeardSound> matcher) {
			return Expectation.of("hear a matching sound", () -> cursor.find(matcher) != null);
		}

		@Override
		public @NotNull List<HeardSound> unread() {
			return cursor.unread();
		}
	}
}
