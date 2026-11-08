package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connects, kicks, rejoins and destroys a player on the release, and tells the server the language its game is set to.
 */
@Release
class SessionReleaseTest {
	@Test
	void connectsAndIsWelcomedWithTheNameItAskedFor(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();

		assertTrue(player.capability(Session.class).state().connected());
		assertEquals(player.name(), player.requestedProfile().getUsername());
	}

	@Test
	void reportsTheLanguageItsGameIsSetTo(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join("ru_ru");

		player.capability(Messages.class).command("anvil-fixture locale");

		assertEquals("ru_ru", server.reply(player, "anvil:locale:"));
	}

	@Test
	void reportsAKickAndRejoins(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();
		Session session = player.capability(Session.class);

		player.capability(Messages.class).command("anvil-fixture kick");

		assertEquals("anvil:requested-kick", session.kicked(Duration.ofSeconds(10)));
		assertTrue(session.state().kickReason().contains("anvil:requested-kick"));
		session.rejoin();
		session.connected(Duration.ofSeconds(20));
	}

	@Test
	void destroyingThePlayerRemovesIt(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();

		player.destroy();

		assertTrue(player.state().destroyed());
		assertTrue(anvil.players().all().isEmpty());
	}
}
