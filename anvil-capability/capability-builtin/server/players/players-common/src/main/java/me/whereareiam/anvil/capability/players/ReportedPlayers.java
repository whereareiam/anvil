package me.whereareiam.anvil.capability.players;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.api.model.platform.PlatformEvent;
import me.whereareiam.anvil.capability.api.platform.ProcessPlatform;
import me.whereareiam.anvil.capability.api.type.PlatformEventType;
import me.whereareiam.anvil.capability.players.model.PlayerVisit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Players capability of one process, derived from the joins and leaves its platform agent reports. The process's own
 * joins and leaves make its visits: on a server the player joining and leaving it, on a proxy the player joining and
 * leaving the proxy itself. A proxy's joins and leaves of its backends make the player's stays on them, which name
 * the backend a player is on.
 *
 * <p>A simulated player is matched by the UUIDs of every profile its client was granted, read on every call, so a
 * profile granted at a later login counts as well.</p>
 */
final class ReportedPlayers implements Players {
	private static final Duration POLL_INTERVAL = Duration.ofMillis(25);

	private final @NotNull String process;
	private final @NotNull ProcessPlatform platform;

	ReportedPlayers(@NotNull String process, @NotNull ProcessPlatform platform) {
		this.process = process;
		this.platform = platform;
	}

	@Override
	public @NotNull List<PlayerVisit> online() {
		return visits(own()).values().stream()
				.filter(visit -> visit.getLeftAt().isEmpty())
				.toList();
	}

	@Override
	public @NotNull Optional<PlayerProfile> profile(@NotNull SimulatedPlayer player) {
		Set<UUID> uniqueIds = uniqueIds(player);

		return online().stream()
				.filter(visit -> uniqueIds.contains(visit.getProfile().getUniqueId()))
				.reduce((first, second) -> second)
				.map(PlayerVisit::getProfile);
	}

	@Override
	public @NotNull Optional<String> backend(@NotNull SimulatedPlayer player) {
		Set<UUID> uniqueIds = uniqueIds(player);
		Map<String, PlatformEvent> open = new LinkedHashMap<>();
		for (PlatformEvent event : platform.events()) {
			if (event.getServer() == null || !uniqueIds.contains(event.getProfile().getUniqueId())) continue;

			open.remove(event.getServer());
			if (event.getType() == PlatformEventType.JOINED) open.put(event.getServer(), event);
		}

		return open.keySet().stream().reduce((first, second) -> second);
	}

	@Override
	public @NotNull PlayerWatch watch() {
		return new Watch(new HistoryCursor<>(platform::events));
	}

	/**
	 * Returns the process's own joins and leaves, in the order it reported them: everything a server reports, and
	 * what a proxy reports of the proxy itself.
	 */
	private @NotNull List<PlatformEvent> own() {
		return platform.events().stream()
				.filter(event -> event.getServer() == null)
				.toList();
	}

	/**
	 * Pairs each join with the leave of the same player from the same place that follows it: the process itself, or
	 * on a proxy one of its backends.
	 *
	 * @return the visits by the position of the join that began them, in join order
	 */
	private static @NotNull Map<Long, PlayerVisit> visits(@NotNull List<PlatformEvent> events) {
		Map<Long, PlayerVisit> visits = new LinkedHashMap<>();
		Map<String, Long> open = new HashMap<>();
		for (PlatformEvent event : events) {
			String stay = event.getProfile().getUniqueId() + "@" + event.getServer();
			if (event.getType() == PlatformEventType.JOINED) {
				open.put(stay, event.getPosition());
				visits.put(event.getPosition(), PlayerVisit.builder()
						.profile(event.getProfile())
						.joinedAt(event.getAt())
						.build());
				continue;
			}

			Long joined = open.remove(stay);
			if (joined == null) continue;

			visits.computeIfPresent(joined, (position, visit) -> visit.toBuilder()
					.leftAt(event.getAt())
					.build());
		}

		return visits;
	}

	/**
	 * Returns the position of the join whose stay a leave ended.
	 */
	private static long joinEndedBy(@NotNull List<PlatformEvent> events, @NotNull PlatformEvent leave) {
		Map<String, Long> open = new HashMap<>();
		for (PlatformEvent event : events) {
			String stay = event.getProfile().getUniqueId() + "@" + event.getServer();
			if (event.getType() == PlatformEventType.JOINED) {
				open.put(stay, event.getPosition());
				continue;
			}

			Long joined = open.remove(stay);
			if (event.getPosition() == leave.getPosition() && joined != null) return joined;
		}

		throw new IllegalStateException("No join of '" + leave.getProfile().getUsername() + "' precedes the leave at "
				+ leave.getAt());
	}

	private static @NotNull Set<UUID> uniqueIds(@NotNull SimulatedPlayer player) {
		return player.grantedProfiles().stream()
				.map(PlayerProfile::getUniqueId)
				.collect(Collectors.toSet());
	}

	private static @NotNull Predicate<PlatformEvent> of(@NotNull SimulatedPlayer player, @Nullable String backend,
			@NotNull PlatformEventType type) {
		return event -> event.getType() == type
				&& Objects.equals(event.getServer(), backend)
				&& uniqueIds(player).contains(event.getProfile().getUniqueId());
	}

	private @NotNull String whoIsOn() {
		List<String> names = online().stream().map(visit -> visit.getProfile().getUsername()).toList();
		return names.isEmpty() ? "nobody is on '" + process + "'" : "on '" + process + "': " + names;
	}

	/**
	 * Checks a condition until it holds, failing with who is on the process once the timeout has passed.
	 */
	private void poll(@NotNull BooleanSupplier condition, @NotNull Duration timeout, @NotNull String failure) {
		if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");

		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) return;

			sleep();
		}
		if (condition.getAsBoolean()) return;

		throw new IllegalStateException(failure + " within " + timeout + "; " + whoIsOn());
	}

	private void sleep() {
		try {
			Thread.sleep(POLL_INTERVAL.toMillis());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while watching the players of '" + process + "'", exception);
		}
	}

	/**
	 * A watch over the joins and leaves the process reports.
	 */
	private final class Watch implements PlayerWatch {
		private final HistoryCursor<PlatformEvent> cursor;

		private Watch(@NotNull HistoryCursor<PlatformEvent> cursor) {
			this.cursor = cursor;
		}

		@Override
		public @NotNull PlayerVisit joined(@NotNull SimulatedPlayer player, @NotNull Duration timeout) {
			return next(of(player, null, PlatformEventType.JOINED), timeout,
					"Player '" + player.name() + "' did not join '" + process + "'");
		}

		@Override
		public @NotNull PlayerVisit left(@NotNull SimulatedPlayer player, @NotNull Duration timeout) {
			return next(of(player, null, PlatformEventType.LEFT), timeout,
					"Player '" + player.name() + "' did not leave '" + process + "'");
		}

		@Override
		public void neverJoined(@NotNull SimulatedPlayer player, @NotNull Duration duration) {
			never(of(player, null, PlatformEventType.JOINED), duration,
					"Player '" + player.name() + "' joined '" + process + "', which it must not join");
		}

		@Override
		public @NotNull Expectation expectJoined(@NotNull SimulatedPlayer player) {
			return expect(of(player, null, PlatformEventType.JOINED), "join '" + process + "'");
		}

		@Override
		public @NotNull PlayerVisit joined(@NotNull SimulatedPlayer player, @NotNull String backend, @NotNull Duration timeout) {
			return next(of(player, backend, PlatformEventType.JOINED), timeout,
					"'" + process + "' did not connect player '" + player.name() + "' to '" + backend + "'");
		}

		@Override
		public @NotNull PlayerVisit left(@NotNull SimulatedPlayer player, @NotNull String backend, @NotNull Duration timeout) {
			return next(of(player, backend, PlatformEventType.LEFT), timeout,
					"'" + process + "' did not move player '" + player.name() + "' away from '" + backend + "'");
		}

		@Override
		public void neverJoined(@NotNull SimulatedPlayer player, @NotNull String backend, @NotNull Duration duration) {
			never(of(player, backend, PlatformEventType.JOINED), duration,
					"'" + process + "' connected player '" + player.name() + "' to '" + backend + "', which it must not join");
		}

		@Override
		public @NotNull Expectation expectJoined(@NotNull SimulatedPlayer player, @NotNull String backend) {
			return expect(of(player, backend, PlatformEventType.JOINED), "be connected to '" + backend + "' by '" + process + "'");
		}

		private void never(@NotNull Predicate<PlatformEvent> join, @NotNull Duration duration, @NotNull String failure) {
			try {
				poll(() -> cursor.find(join) != null, duration, "joined");
			} catch (IllegalStateException quiet) {
				if (cursor.find(join) == null) return;
			}

			throw new IllegalStateException(failure);
		}

		private @NotNull Expectation expect(@NotNull Predicate<PlatformEvent> join, @NotNull String description) {
			return Expectation.of(description, () -> cursor.find(join) != null);
		}

		private @NotNull PlayerVisit next(@NotNull Predicate<PlatformEvent> matcher, @NotNull Duration timeout, @NotNull String failure) {
			poll(() -> cursor.find(matcher) != null, timeout, failure);
			PlatformEvent event = Objects.requireNonNull(cursor.take(matcher));
			List<PlatformEvent> events = platform.events();
			if (event.getType() == PlatformEventType.JOINED)
				return Objects.requireNonNull(visits(events).get(event.getPosition()));

			return Objects.requireNonNull(visits(events).get(joinEndedBy(events, event)));
		}
	}
}
