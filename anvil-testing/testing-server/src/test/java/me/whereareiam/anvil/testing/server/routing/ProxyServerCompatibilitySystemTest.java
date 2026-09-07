package me.whereareiam.anvil.testing.server.routing;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.testing.server.extension.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testing.server.scenario.CompatibilityScenarioCatalog;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("compatibility")
class ProxyServerCompatibilitySystemTest {
	@TestFactory
	Stream<DynamicTest> runsEveryNativePlatformAndVersion() {
		ScenarioRegistry registry = new ScenarioRegistry();
		new CompatibilityScenarioCatalog().register(registry);
		String filter = System.getProperty("anvil.matrix.filter", ".*");

		return registry.scenarios().stream()
				.filter(scenario -> scenario.getName().matches(filter))
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> verify(scenario)));
	}

	private void verify(AnvilScenario scenario) {
		EngineOptions options = EngineOptions.builder().eulaAccepted(true).build();
		try (ScenarioEngine engine = AnvilLauncher.create(options); var context = engine.start(scenario)) {
			SimulatedPlayer alice = context.players().create("Alice");
			FixtureAgentProbeProvider.Probe probe = alice.capability(FixtureAgentProbeProvider.Probe.class);
			for (var process : context.processes().servers())
				assertEquals("external:matrix", probe.echo(process.name(), "matrix"));
			Session session = alice.capability(Session.class);
			Messages messages = alice.capability(Messages.class);
			Server server = alice.capability(Server.class);
			session.connect();
			session.connected(Duration.ofSeconds(30));
			messages.received("anvil:welcome", Duration.ofSeconds(20));
			messages.command("anvil-fixture ping");
			messages.received("anvil:pong", Duration.ofSeconds(10));

			if (scenario.getProxies().isEmpty()) {
				assertEquals("server", server.joined("server", Duration.ofSeconds(10)).getServer());
				return;
			}

			PlayerIdentity initial = server.joined("lobby", Duration.ofSeconds(10));
			assertEquals("lobby", initial.getServer());
			messages.command("anvil-fixture transfer game");
			server.joined("game", Duration.ofSeconds(15));
			messages.command("anvil-fixture ping");
			messages.received("anvil:pong", Duration.ofSeconds(10));
		}
	}
}
