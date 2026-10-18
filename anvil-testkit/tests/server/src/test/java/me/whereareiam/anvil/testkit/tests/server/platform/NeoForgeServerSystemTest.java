package me.whereareiam.anvil.testkit.tests.server.platform;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logs a player in on every NeoForge release, which loads no fixture plugin, and drives it with vanilla commands.
 */
class NeoForgeServerSystemTest {
	@TestFactory
	Stream<DynamicTest> runsEveryNeoForgeRelease() {
		return PlatformSelection.select(CompatibilityScenarioFactory.neoForgeScenarios()).stream()
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> verify(scenario)));
	}

	private void verify(AnvilScenario scenario) {
		EngineOptions options = EngineOptions.builder().eulaAccepted(true).build();
		try (ScenarioEngine engine = AnvilLauncher.create(options); var context = engine.start(scenario)) {
			SimulatedPlayer alice = context.players().create("Alice");
			Session session = alice.capability(Session.class);
			Messages messages = alice.capability(Messages.class);
			Players players = context.processes().server("server").capability(Players.class);
			Dispatch dispatch = context.processes().server("server").capability(Dispatch.class);
			PlayerWatch arrivals = players.watch();

			session.connect();
			session.connected(Duration.ofSeconds(30));
			assertEquals("Alice", arrivals.joined(alice, Duration.ofSeconds(10)).getProfile().getUsername());

			assertTrue(dispatch.execute("tellraw Alice \"anvil:neoforge\""));
			messages.received("anvil:neoforge", Duration.ofSeconds(10));

			assertTrue(dispatch.execute("kick Alice anvil:kicked"));
			assertEquals("anvil:kicked", session.kicked());
			assertFalse(session.state().connected());
			arrivals.left(alice, Duration.ofSeconds(10));
			assertEquals(List.of(), players.online());
		}
	}
}
