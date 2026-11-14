package me.whereareiam.anvil.capability.vitals;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.vitals.channel.VitalsChannel;
import me.whereareiam.anvil.capability.vitals.model.Death;
import me.whereareiam.anvil.capability.vitals.model.VitalStatus;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Vitals capability driven over a player's typed channel: the worker's latest status is the player's, every death it
 * emits is kept in the player's history across reconnects, and a respawn waits for a status that is alive again. A
 * death the client does not respawn from at once leaves the player dead until a status reports it alive, since the
 * server sends the zero health only after the death. The channel releases the event subscriptions together with the
 * player.
 */
final class ChannelVitals implements Vitals {
	private static final String DEATH = "die";
	private static final String RESPAWN = "respawn";

	private final ProtocolChannel channel;
	private final CopyOnWriteArrayList<Death> deaths = new CopyOnWriteArrayList<>();
	private volatile VitalStatus status = VitalStatus.INITIAL;
	private volatile boolean deathScreen;
	private volatile long reports;

	ChannelVitals(@NotNull ProtocolChannel channel) {
		this.channel = channel;
		channel.subscribe(VitalsChannel.STATUS, this::report);
		channel.subscribe(VitalsChannel.DIED, this::died);
	}

	@Override
	public float health() {
		return status.getHealth();
	}

	@Override
	public int food() {
		return status.getFood();
	}

	@Override
	public float saturation() {
		return status.getSaturation();
	}

	@Override
	public boolean dead() {
		return deathScreen || status.getHealth() <= 0F;
	}

	@Override
	public @NotNull List<Death> deaths() {
		return List.copyOf(deaths);
	}

	@Override
	public @NotNull DeathWatch watchDeaths() {
		return new Watch(new HistoryCursor<>(this::deaths));
	}

	@Override
	public void respawn(@NotNull Duration timeout) {
		if (!dead()) throw new IllegalStateException("Player cannot respawn, it is not dead (health " + health() + ")");

		long before = reports;
		channel.request(VitalsChannel.RESPAWN, null);
		channel.await(() -> reports > before && !dead(), RESPAWN, timeout);
	}

	private synchronized void report(VitalStatus reported) {
		status = reported;
		if (reported.getHealth() > 0F) deathScreen = false;
		reports++;
	}

	private synchronized void died(Death death) {
		deaths.add(death);
		if (!death.isRespawnedImmediately()) deathScreen = true;
	}

	/**
	 * Follows the player's deaths through a cursor and waits through the player's channel.
	 */
	private final class Watch implements DeathWatch {
		private final HistoryCursor<Death> cursor;

		private Watch(HistoryCursor<Death> cursor) {
			this.cursor = cursor;
		}

		@Override
		public @NotNull Death died(@NotNull Predicate<Death> matcher, @NotNull Duration timeout) {
			channel.await(() -> cursor.find(matcher) != null, DEATH, timeout);
			return Objects.requireNonNull(cursor.take(matcher));
		}

		@Override
		public void neverDied(@NotNull Predicate<Death> matcher, @NotNull Duration duration) {
			try {
				channel.await(() -> cursor.find(matcher) != null, DEATH, duration);
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			Death death = Objects.requireNonNull(cursor.find(matcher));
			throw new IllegalStateException("Player died when it must not: " + death.getMessage());
		}

		@Override
		public @NotNull Expectation expectDied(@NotNull Predicate<Death> matcher) {
			return Expectation.of(DEATH, () -> cursor.find(matcher) != null);
		}

		@Override
		public @NotNull List<Death> unread() {
			return cursor.unread();
		}
	}
}
