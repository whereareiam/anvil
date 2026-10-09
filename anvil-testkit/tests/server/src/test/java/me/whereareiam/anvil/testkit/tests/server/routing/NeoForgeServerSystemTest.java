package me.whereareiam.anvil.testkit.tests.server.routing;

import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.console.Console;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("compatibility")
class NeoForgeServerSystemTest {
	@TestFactory
	Stream<DynamicTest> runsEveryNeoForgeVersion() {
		String filter = System.getProperty("anvil.matrix.filter", ".*");
		return CompatibilityScenarioFactory.neoForgeScenarios().stream()
				.filter(scenario -> scenario.getName().matches(filter))
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> verify(scenario)));
	}

	private void verify(AnvilScenario scenario) {
		EngineOptions options = EngineOptions.builder().eulaAccepted(true).build();
		try (ScenarioEngine engine = AnvilLauncher.create(options); var context = engine.start(scenario)) {
			SimulatedPlayer alice = context.players().create("Alice");
			Session session = alice.capability(Session.class);
			Messages messages = alice.capability(Messages.class);
			Server server = alice.capability(Server.class);
			Console console = context.processes().server("server").capability(Console.class);

			session.connect();
			session.connected(Duration.ofSeconds(30));
			assertEquals("server", server.joined("server", Duration.ofSeconds(10)).getRoute().getServer());

			assertTrue(console.execute("tellraw Alice \"anvil:neoforge\""));
			messages.received("anvil:neoforge", Duration.ofSeconds(10));

			assertTrue(console.execute("kick Alice anvil:kicked"));
			assertEquals("anvil:kicked", session.kicked());
			assertFalse(session.state().connected());
		}
	}
}
