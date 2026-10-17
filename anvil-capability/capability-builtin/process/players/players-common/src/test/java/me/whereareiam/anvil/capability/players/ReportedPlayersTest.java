package me.whereareiam.anvil.capability.players;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.players.model.PlayerVisit;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static me.whereareiam.anvil.capability.players.ReportingProcess.player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportedPlayersTest {
	private static final Duration WAIT = Duration.ofMillis(200);

	private final PlayerProfile offline = new PlayerProfile("Alice", UUID.randomUUID());
	private final PlayerProfile premium = new PlayerProfile("Alice_Premium", UUID.randomUUID());
	private final PlayerProfile bob = new PlayerProfile("Bob", UUID.randomUUID());
	private final SimulatedPlayer alice = player("Alice", offline, premium);

	@Test
	void listsThePlayersOnAServerAndTheProfileItReportsForThem() {
		ReportingProcess lobby = new ReportingProcess(false)
				.joined(offline, null)
				.left(offline, null)
				.joined(bob, null)
				.joined(premium, null);
		Players players = new ReportedPlayers("lobby", lobby);

		assertEquals(List.of(bob, premium), players.online().stream().map(PlayerVisit::getProfile).toList());
		assertEquals(Optional.of(premium), players.profile(alice));
		assertEquals(Optional.empty(), players.profile(player("Carol")));
		assertEquals(Optional.empty(), players.backend(alice));
	}

	@Test
	void knowsAPlayerOnAProxyBeforeItIsOnAnyBackend() {
		ReportingProcess proxy = new ReportingProcess(true).joined(offline, null);
		Players players = new ReportedPlayers("proxy", proxy);

		assertEquals(Optional.of(offline), players.profile(alice));
		assertEquals(Optional.empty(), players.backend(alice));

		proxy.joined(offline, "auth").left(offline, "auth").joined(offline, "lobby");
		assertEquals(Optional.of("lobby"), players.backend(alice));
		assertEquals(1, players.online().size());

		proxy.left(offline, "lobby").left(offline, null);
		assertEquals(Optional.empty(), players.backend(alice));
		assertEquals(Optional.empty(), players.profile(alice));
		assertEquals(List.of(), players.online());
	}

	@Test
	void watchesJoinsAndLeavesInOrderFromWhenItStarted() {
		ReportingProcess lobby = new ReportingProcess(false).joined(offline, null).left(offline, null);
		PlayerWatch watch = new ReportedPlayers("lobby", lobby).watch();
		Expectation joins = watch.expectJoined(alice);

		assertFalse(joins.met());
		assertThrows(IllegalStateException.class, () -> watch.joined(alice, WAIT));

		lobby.joined(bob, null).joined(premium, null).left(premium, null);

		assertTrue(joins.met());
		PlayerVisit visit = watch.joined(alice, WAIT);
		assertEquals(premium, visit.getProfile());
		assertEquals(Optional.of(Instant.ofEpochSecond(5)), visit.getLeftAt());
		assertEquals(Instant.ofEpochSecond(4), watch.left(alice, WAIT).getJoinedAt());
		watch.neverJoined(alice, WAIT);
		assertThrows(IllegalStateException.class, () -> watch.joined(alice, WAIT));
	}

	@Test
	void ignoresAProxysJoinsOfItsBackendsInTheWatch() {
		ReportingProcess proxy = new ReportingProcess(true);
		PlayerWatch watch = new ReportedPlayers("proxy", proxy).watch();
		proxy.joined(offline, "auth");

		watch.neverJoined(alice, WAIT);
		proxy.joined(offline, null);
		assertEquals(offline, watch.joined(alice, WAIT).getProfile());
	}

	@Test
	void waitsForAProxyToConnectAPlayerToABackendAndAway() {
		ReportingProcess proxy = new ReportingProcess(true).joined(offline, null);
		PlayerWatch watch = new ReportedPlayers("proxy", proxy).watch();
		Expectation game = watch.expectJoined(alice, "game");

		assertFalse(game.met());
		proxy.joined(offline, "lobby").left(offline, "lobby").joined(offline, "game");

		assertTrue(game.met());
		assertEquals(Instant.ofEpochSecond(2), watch.joined(alice, "lobby", WAIT).getJoinedAt());
		assertEquals(Optional.of(Instant.ofEpochSecond(3)), watch.left(alice, "lobby", WAIT).getLeftAt());
		assertEquals(Optional.empty(), watch.joined(alice, "game", WAIT).getLeftAt());
		watch.neverJoined(alice, "lobby", WAIT);

		proxy.left(offline, "game").left(offline, null);
		assertEquals(Instant.ofEpochSecond(4), watch.left(alice, "game", WAIT).getJoinedAt());
		assertEquals(Instant.ofEpochSecond(1), watch.left(alice, WAIT).getJoinedAt());
	}

	@Test
	void refusesABackendThePlayerWasConnectedToSinceThePreviousWait() {
		ReportingProcess proxy = new ReportingProcess(true);
		PlayerWatch watch = new ReportedPlayers("proxy", proxy).watch();
		proxy.joined(offline, null).joined(offline, "auth").left(offline, "auth");

		IllegalStateException joined = assertThrows(IllegalStateException.class, () -> watch.neverJoined(alice, "auth", WAIT));
		assertEquals("'proxy' connected player 'Alice' to 'auth', which it must not join", joined.getMessage());
		watch.neverJoined(alice, "lobby", WAIT);

		IllegalStateException missing = assertThrows(IllegalStateException.class, () -> watch.joined(alice, "lobby", WAIT));
		assertTrue(missing.getMessage().startsWith("'proxy' did not connect player 'Alice' to 'lobby' within"), missing.getMessage());
	}

	@Test
	void refusesAJoinSinceThePreviousWaitHoweverBriefAndNamesWhoIsOn() {
		ReportingProcess auth = new ReportingProcess(false);
		PlayerWatch watch = new ReportedPlayers("auth", auth).watch();
		auth.joined(offline, null).left(offline, null).joined(bob, null);

		IllegalStateException joined = assertThrows(IllegalStateException.class, () -> watch.neverJoined(alice, WAIT));
		assertEquals("Player 'Alice' joined 'auth', which it must not join", joined.getMessage());

		IllegalStateException missing = assertThrows(IllegalStateException.class,
				() -> new ReportedPlayers("auth", auth).watch().joined(alice, WAIT));
		assertTrue(missing.getMessage().startsWith("Player 'Alice' did not join 'auth' within"), missing.getMessage());
		assertTrue(missing.getMessage().endsWith("on 'auth': [Bob]"), missing.getMessage());
	}
}
