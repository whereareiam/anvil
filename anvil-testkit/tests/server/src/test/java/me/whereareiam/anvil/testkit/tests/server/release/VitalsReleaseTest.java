package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.process.ProcessConsole;
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
 * server turned the death screen off.
 *
 * <p>From Minecraft 1.21.4 a server protects a player from damage until it handles the client's report that it
 * loaded, a tick after the join the fixture's welcome confirms, so each test repeats its damaging command until it
 * lands.</p>
 */
@Release
class VitalsReleaseTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final Duration ATTEMPT = Duration.ofSeconds(1);
	private static final int ATTEMPTS = 5;

	@Test
	void seesTheHealthItLoses(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Vitals vitals = player.capability(Vitals.class);

		Expectation hurt = Expectation.of("lose health", () -> vitals.health() < 20F);
		repeatUntil(player, hurt, () -> server.console("effect give " + player.name() + " minecraft:instant_damage 1 0"));

		assertFalse(vitals.dead());
		assertTrue(vitals.food() > 0);
	}

	@Test
	void diesUntilItRespawnsAndThenHasFullHealth(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Vitals vitals = player.capability(Vitals.class);
		DeathWatch deaths = vitals.watchDeaths();

		kill(server, player, deaths);

		Death death = deaths.died(TIMEOUT);
		assertTrue(death.getMessage().contains(player.name()), death.getMessage());
		assertFalse(death.isRespawnedImmediately());
		assertTrue(vitals.dead());
		deaths.neverDied(ATTEMPT);
		assertTrue(vitals.dead());

		vitals.respawn(TIMEOUT);
		assertFalse(vitals.dead());
		assertEquals(20F, vitals.health());
		assertEquals(List.of(death), vitals.deaths());
	}

	@Test
	void respawnsByItselfWhenTheServerTurnedTheDeathScreenOff(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		immediateRespawn(anvil, true);
		try {
			SimulatedPlayer player = server.join();
			Vitals vitals = player.capability(Vitals.class);
			DeathWatch deaths = vitals.watchDeaths();

			kill(server, player, deaths);

			assertTrue(deaths.died(TIMEOUT).isRespawnedImmediately());
			player.awaitFirst(TIMEOUT, Expectation.of("respawn", () -> vitals.health() == 20F && !vitals.dead()));
		} finally {
			immediateRespawn(anvil, false);
		}
	}

	/**
	 * Turns the death screen off or back on and waits until the server confirms it, so the next player's login, also
	 * in the next test on the same server, carries the rule.
	 */
	private static void immediateRespawn(ScenarioContext anvil, boolean enabled) {
		String rule = TestedRelease.isAtLeast("1.21.11") ? "immediate_respawn" : "doImmediateRespawn";
		ProcessConsole console = anvil.processes().server("server").console();
		ConsoleWatch output = console.watch();

		console.sendCommand("gamerule " + rule + " " + enabled);
		output.printed(line -> line.contains(rule) && line.contains(String.valueOf(enabled)), TIMEOUT);
	}

	/**
	 * Kills the player until the server reports the death, without taking it from the watch.
	 */
	private static void kill(FixtureServer server, SimulatedPlayer player, DeathWatch deaths) {
		repeatUntil(player, deaths.expectDied(death -> true), () -> server.console("kill " + player.name()));
	}

	private static void repeatUntil(SimulatedPlayer player, Expectation landed, Runnable command) {
		for (int attempt = 1; ; attempt++) {
			command.run();
			try {
				player.awaitFirst(ATTEMPT, landed);
				return;
			} catch (IllegalStateException notYet) {
				if (attempt == ATTEMPTS) throw notYet;
			}
		}
	}
}
