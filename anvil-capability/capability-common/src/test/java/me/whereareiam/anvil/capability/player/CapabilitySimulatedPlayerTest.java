package me.whereareiam.anvil.capability.player;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
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
		SimulatedPlayer player = compose(new StubPlayer(channel));

		int first = player.awaitFirst(TIMEOUT,
				Expectation.of("receive a reply", () -> false),
				Expectation.of("be kicked", () -> true));

		assertEquals(1, first);
		assertEquals(List.of("receive a reply or be kicked"), channel.waits);
	}

	@Test
	void prefersTheEarliestExpectationWhenSeveralAreMet() {
		SimulatedPlayer player = compose(new StubPlayer(new RecordingChannel()));

		assertEquals(0, player.awaitFirst(TIMEOUT, Expectation.of("a", () -> true), Expectation.of("b", () -> true)));
	}

	@Test
	void failsWithEveryExpectationNamedWhenTheChannelGivesUp() {
		SimulatedPlayer player = compose(new StubPlayer(new RecordingChannel()));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> player.awaitFirst(TIMEOUT, Expectation.of("receive a reply", () -> false), Expectation.of("be kicked", () -> false)));

		assertEquals("Player did not receive a reply or be kicked", failure.getMessage());
	}

	@Test
	void checksAgainUntilAnExpectationIsMetWhenThePlayerHasNoChannel() {
		SimulatedPlayer player = compose(new StubPlayer(null));
		AtomicInteger checks = new AtomicInteger();

		int first = player.awaitFirst(TIMEOUT,
				Expectation.of("never", () -> false),
				Expectation.of("on the third check", () -> checks.incrementAndGet() >= 3));

		assertEquals(1, first);
		assertEquals(3, checks.get());
	}

	@Test
	void failsWhenNoExpectationIsMetInTimeWithoutAChannel() {
		SimulatedPlayer player = compose(new StubPlayer(null));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> player.awaitFirst(Duration.ofMillis(60), Expectation.of("be kicked", () -> false)));

		assertTrue(failure.getMessage().startsWith("Player 'Alice' did not be kicked within"), failure.getMessage());
	}

	@Test
	void refusesAWaitForNothing() {
		SimulatedPlayer player = compose(new StubPlayer(null));

		assertThrows(IllegalArgumentException.class, () -> player.awaitFirst(TIMEOUT));
	}

	private static SimulatedPlayer compose(ProtocolPlayer player) {
		return new PlayerCapabilityRuntime(List.of()).compose(player, null, PlayerConnection.to("proxy"), ignored -> { });
	}

	/**
	 * Checks a wait's condition once, as a channel whose observation is already complete; a condition that does
	 * not hold fails at once instead of timing out.
	 */
	private static final class RecordingChannel implements ProtocolChannel {
		private final List<String> waits = new ArrayList<>();

		public <Q, R> R request(@NotNull OperationDescriptor<Q, R> operation, Q request) { throw new AssertionError(); }
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) { return () -> { }; }
		public @NotNull Set<String> installedCapabilities() { return Set.of(); }
		public @NotNull Map<String, String> unavailableCapabilities() { return Map.of(); }

		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			waits.add(description);
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}
	}

	private record StubPlayer(@Nullable ProtocolChannel protocolChannel) implements ProtocolPlayer {
		public @NotNull String name() { return "Alice"; }
		public @NotNull String libraryId() { return "stub"; }
		public @NotNull String clientVersion() { return "test"; }
		public @NotNull ProtocolRelease release() {
			return ProtocolRelease.builder()
					.libraryVersion("test")
					.minecraftVersion(MinecraftVersion.parse("1.21.11"))
					.protocolNumber(774)
					.javaVersion(21)
					.build();
		}
		public @NotNull PlayerProfile requestedProfile() { return new PlayerProfile("Alice", UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8))); }
		public @Nullable PlayerProfile grantedProfile() { return null; }
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) { return Optional.empty(); }
		public @NotNull Optional<ProtocolChannel> channel() { return Optional.ofNullable(protocolChannel); }
		public boolean destroyed() { return false; }
		public void destroy() { }
	}
}
