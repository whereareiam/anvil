package me.whereareiam.anvil.testkit.tests.server.platform;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logs a player in on Spigot and behind each proxy, runs console commands and an agent extension on every process,
 * and moves the player between the backends of a proxy.
 */
class PlatformCompatibilitySystemTest {
	@TestFactory
	Stream<DynamicTest> runsEveryPlatformScenario() {
		return PlatformSelection.select(CompatibilityScenarioFactory.platformScenarios()).stream()
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> verify(scenario)));
	}

	private void verify(AnvilScenario scenario) {
		EngineOptions options = EngineOptions.builder().eulaAccepted(true).build();
		try (ScenarioEngine engine = AnvilLauncher.create(options); var context = engine.start(scenario)) {
			SimulatedPlayer alice = context.players().create("Alice");
			for (var process : context.processes().servers()) {
				assertEquals("external:matrix", process.capability(FixtureAgentProbeProvider.Probe.class).echo("matrix"));
				assertTrue(process.capability(Dispatch.class).execute("list"), process.name() + " must accept a console command");
			}
			for (var proxy : scenario.getProxies()) {
				String command = proxy.getPlatform().equals(Platforms.VELOCITY) ? "velocity version" : "glist";
				assertTrue(context.processes().get(proxy.getName()).capability(Dispatch.class).execute(command),
						proxy.getName() + " must accept a console command");
			}
			Session session = alice.capability(Session.class);
			Messages messages = alice.capability(Messages.class);
			Players first = context.processes().server(scenario.getProxies().isEmpty() ? "server" : "lobby").capability(Players.class);
			PlayerWatch arrivals = first.watch();
			Players proxy = scenario.getProxies().isEmpty() ? null
					: context.processes().proxy(scenario.getProxies().getFirst().getName()).capability(Players.class);
			PlayerWatch moves = proxy == null ? null : proxy.watch();
			session.connect();
			session.connected(Duration.ofSeconds(30));
			messages.received("anvil:welcome", Duration.ofSeconds(20));
			messages.command("anvil-fixture ping");
			messages.received("anvil:pong", Duration.ofSeconds(10));
			arrivals.joined(alice, Duration.ofSeconds(10));
			assertEquals("Alice", first.profile(alice).orElseThrow().getUsername());
			if (scenario.getProxies().isEmpty()) return;

			moves.joined(alice, Duration.ofSeconds(10));
			moves.joined(alice, "lobby", Duration.ofSeconds(10));
			assertEquals(Optional.of("lobby"), proxy.backend(alice));
			PlayerWatch transfers = context.processes().server("game").capability(Players.class).watch();
			messages.command("anvil-fixture transfer game");
			transfers.joined(alice, Duration.ofSeconds(15));
			arrivals.left(alice, Duration.ofSeconds(10));
			moves.left(alice, "lobby", Duration.ofSeconds(10));
			moves.joined(alice, "game", Duration.ofSeconds(10));
			moves.neverJoined(alice, "lobby", Duration.ofSeconds(1));
			assertEquals(Optional.of("game"), proxy.backend(alice));
			assertEquals("Alice", proxy.profile(alice).orElseThrow().getUsername());
			messages.command("anvil-fixture ping");
			messages.received("anvil:pong", Duration.ofSeconds(10));
		}
	}
}
