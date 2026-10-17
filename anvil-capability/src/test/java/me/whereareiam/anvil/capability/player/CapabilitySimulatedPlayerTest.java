package me.whereareiam.anvil.capability.player;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.player.CapabilityPlayer;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolCapabilityPlayer;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitySimulatedPlayerTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	@Test
	void waitsThroughThePlayersChannelAndReturnsTheExpectationMet() {
		RecordingChannel channel = new RecordingChannel();
		SimulatedPlayer player = compose(new ChannelPlayer(channel));

		int first = player.awaitFirst(TIMEOUT,
				Expectation.of("receive a reply", () -> false),
				Expectation.of("be kicked", () -> true));

		assertEquals(1, first);
		assertEquals(List.of("receive a reply or be kicked"), channel.waits);
	}

	@Test
	void prefersTheEarliestExpectationWhenSeveralAreMet() {
		SimulatedPlayer player = compose(new ChannelPlayer(new RecordingChannel()));

		assertEquals(0, player.awaitFirst(TIMEOUT, Expectation.of("a", () -> true), Expectation.of("b", () -> true)));
	}

	@Test
	void failsWithEveryExpectationNamedWhenTheChannelGivesUp() {
		SimulatedPlayer player = compose(new ChannelPlayer(new RecordingChannel()));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> player.awaitFirst(TIMEOUT, Expectation.of("receive a reply", () -> false), Expectation.of("be kicked", () -> false)));

		assertEquals("Player did not receive a reply or be kicked", failure.getMessage());
	}

	@Test
	void checksAgainUntilAnExpectationIsMetWhenThePlayerHasNoChannel() {
		SimulatedPlayer player = compose(new PlainPlayer());
		AtomicInteger checks = new AtomicInteger();

		int first = player.awaitFirst(TIMEOUT,
				Expectation.of("never", () -> false),
				Expectation.of("on the third check", () -> checks.incrementAndGet() >= 3));

		assertEquals(1, first);
		assertEquals(3, checks.get());
	}

	@Test
	void failsWhenNoExpectationIsMetInTimeWithoutAChannel() {
		SimulatedPlayer player = compose(new PlainPlayer());

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> player.awaitFirst(Duration.ofMillis(60), Expectation.of("be kicked", () -> false)));

		assertTrue(failure.getMessage().startsWith("Player 'Alice' did not be kicked within"), failure.getMessage());
	}

	@Test
	void refusesAWaitForNothing() {
		SimulatedPlayer player = compose(new PlainPlayer());

		assertThrows(IllegalArgumentException.class, () -> player.awaitFirst(TIMEOUT));
	}

	private static SimulatedPlayer compose(CapabilityPlayer player) {
		return new PlayerCapabilityRuntime(List.of()).compose(player, PlayerConnection.to("proxy"), ignored -> { });
	}


	/**
	 * Checks a wait's condition once, as a channel whose observation is already complete; a condition that does
	 * not hold fails at once instead of timing out.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> waits = new ArrayList<>();

		public <Q, R> R request(@NotNull ChannelOperation<Q, R> channelOperation, Q request) { throw new AssertionError(); }
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) { return () -> { }; }
		public @NotNull Set<String> installedCapabilities() { return Set.of(); }
		public @NotNull Map<String, String> unavailableCapabilities() { return Map.of(); }

		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			waits.add(description);
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}
	}

	private record ChannelPlayer(CapabilityChannel capabilityChannel) implements ProtocolCapabilityPlayer {
		public @NotNull String name() { return "Alice"; }
		public @NotNull String libraryId() { return "stub"; }
		public @NotNull String clientVersion() { return "test"; }
		public @NotNull PlayerProfile requestedProfile() { return new PlayerProfile("Alice", UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8))); }
		public @Nullable PlayerProfile grantedProfile() { return null; }
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) { return Optional.empty(); }
		public @NotNull Optional<CapabilityChannel> channel() { return Optional.of(capabilityChannel); }
		public boolean destroyed() { return false; }
		public void destroy() { }
	}

	private static final class PlainPlayer implements CapabilityPlayer {
		public @NotNull String name() { return "Alice"; }
		public @NotNull String clientVersion() { return "test"; }
		public @NotNull PlayerProfile requestedProfile() { return new PlayerProfile("Alice", UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8))); }
		public @Nullable PlayerProfile grantedProfile() { return null; }
		public boolean destroyed() { return false; }
		public void destroy() { }
	}
}
