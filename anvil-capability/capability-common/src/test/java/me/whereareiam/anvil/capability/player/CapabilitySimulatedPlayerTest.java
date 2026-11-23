package me.whereareiam.anvil.capability.player;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.player.api.ProtocolPlayer;
import me.whereareiam.anvil.player.api.channel.ProtocolChannel;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.player.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitySimulatedPlayerTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	@Test
	void waitsThroughThePlayersChannelAndReturnsTheExpectationMet() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of());
		SimulatedPlayer player = compose(new StubPlayer(channel));

		int first = player.awaitFirst(TIMEOUT,
				Expectation.of("receive a reply", () -> false),
				Expectation.of("be kicked", () -> true));

		assertEquals(1, first);
		assertEquals(List.of("receive a reply or be kicked"), channel.waits());
	}

	@Test
	void prefersTheEarliestExpectationWhenSeveralAreMet() {
		SimulatedPlayer player = compose(new StubPlayer(new TestProtocolChannel(Set.of())));

		assertEquals(0, player.awaitFirst(TIMEOUT, Expectation.of("a", () -> true), Expectation.of("b", () -> true)));
	}

	@Test
	void failsWithEveryExpectationNamedWhenTheChannelGivesUp() {
		SimulatedPlayer player = compose(new StubPlayer(new TestProtocolChannel(Set.of())));

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

	private record StubPlayer(@Nullable ProtocolChannel protocolChannel) implements ProtocolPlayer {
		public @NotNull String name() { return "Alice"; }
		public @NotNull String libraryId() { return "stub"; }
		public @NotNull MinecraftVersion clientVersion() { return MinecraftVersion.parse("1.21.11"); }
		public @NotNull ProtocolRelease release() {
			return ProtocolRelease.builder()
					.libraryVersion("test")
					.minecraftVersion(MinecraftVersion.parse("1.21.11"))
					.protocolNumber(774)
					.javaVersion(21)
					.build();
		}
		public @NotNull PlayerProfile requestedProfile() {
			return new PlayerProfile("Alice", UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8)));
		}
		public @Nullable PlayerProfile grantedProfile() { return null; }
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) { return Optional.empty(); }
		public @NotNull Optional<ProtocolChannel> channel() { return Optional.ofNullable(protocolChannel); }
		public boolean destroyed() { return false; }
		public void destroy() { }
	}
}
