package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.vitals.DeathWatch;
import me.whereareiam.anvil.capability.vitals.Vitals;
import me.whereareiam.anvil.capability.vitals.model.Death;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sees the health the server sends the player, dies to the server's vanilla commands and respawns, by itself when the
 * server turned the death screen off. A server protects a player that just spawned from ordinary damage, so the test
 * sets the player's health through the fixture to see it fall, and kills with {@code /kill}, which the protection
 * does not stop.
 */
@Release
class VitalsReleaseTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final Duration QUIET = Duration.ofSeconds(1);

	@Test
	void seesTheHealthItLoses(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Vitals vitals = player.capability(Vitals.class);

		server.console("anvil-health " + player.name() + " 14");
		player.awaitFirst(TIMEOUT, Expectation.of("lose health", () -> vitals.health() == 14F));

		assertFalse(vitals.dead());
		assertTrue(vitals.food() > 0);
	}

	@Test
	void diesUntilItRespawnsAndThenHasFullHealth(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Vitals vitals = player.capability(Vitals.class);
		DeathWatch deaths = vitals.watchDeaths();

		server.console("kill " + player.name());

		Death death = deaths.died(TIMEOUT);
		assertTrue(death.message().contains(player.name()), death.message());
		assertFalse(death.respawnedImmediately());
		assertTrue(vitals.dead());
		deaths.neverDied(QUIET);
		assertTrue(vitals.dead());

		vitals.respawn(TIMEOUT);
		assertFalse(vitals.dead());
		assertEquals(20F, vitals.health());
		assertEquals(List.of(death), vitals.deaths());
	}

	@Test
	void respawnsByItselfWhenTheServerTurnedTheDeathScreenOff(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		immediateRespawn(server, true);
		try {
			SimulatedPlayer player = server.join();
			Vitals vitals = player.capability(Vitals.class);
			DeathWatch deaths = vitals.watchDeaths();

			server.console("kill " + player.name());

			assertTrue(deaths.died(TIMEOUT).respawnedImmediately());
			player.awaitFirst(TIMEOUT, Expectation.of("respawn", () -> vitals.health() == 20F && !vitals.dead()));
		} finally {
			immediateRespawn(server, false);
		}
	}

	/**
	 * Turns the death screen off or back on, so the next player's login, also in the next test on the same server,
	 * carries the rule.
	 */
	private static void immediateRespawn(FixtureServer server, boolean enabled) {
		String rule = TestedRelease.isAtLeast("1.21.11") ? "immediate_respawn" : "doImmediateRespawn";
		server.console("gamerule " + rule + " " + enabled);
	}
}
