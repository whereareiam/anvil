package me.whereareiam.anvil.testkit.tests.server.behavior;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PartialScenarioLifecycleSystemTest {
	@Test
	void controlsIndividualProcessesThenCompletesTwoProxyThreeServerEnvironment() throws Exception {
		AnvilScenario original = CompatibilityScenarioFactory.proxyScenario(Platforms.VELOCITY, Platforms.PAPER,
				MinecraftVersion.parse("1.21.11"));
		var auxiliary = original.getServers().getFirst().toBuilder().name("auxiliary").build();
		var proxy = original.getProxies().getFirst().toBuilder()
				.server("auxiliary").setting("advanced.login-ratelimit", "0").build();
		AtomicInteger setup = new AtomicInteger();
		AnvilScenario network = original.toBuilder().name("partial-network")
				.processTimeouts(ProcessTimeouts.builder().startup(Duration.ofMinutes(2)).build())
				.server(auxiliary).clearProxies().proxy(proxy).proxy(proxy.toBuilder().name("secondary").defaultServer("auxiliary").build())
				.setupHook(context -> setup.incrementAndGet()).build();
		List<RunningProcess> retained;
		try (var engine = AnvilLauncher.create(EngineOptions.builder().eulaAccepted(true)
				.workDirectory(Path.of("build", "partial-scenario-live"))
				.processTimeouts(ProcessTimeouts.builder().startup(Duration.ofMillis(1)).shutdown(Duration.ofSeconds(5)).build())
				.build());
		     var context = engine.prepare(network)) {
			boolean successful = false;
			try {
				assertTrue(context.processes().all().isEmpty());
				assertNull(context.definition().getProcessTimeouts().getShutdown());
				assertEquals(0, setup.get());
				var gateway = context.processes().start("proxy");
				assertNotNull(gateway.executionId());
				assertEquals(1, context.processes().all().size());
				var lobby = context.processes().start("lobby");
				assertNotNull(lobby.executionId());
				lobby.files().write("partial-marker", "retained");
				var alice = context.players().create("PartialAlice");
				Session connection = alice.capability(Session.class);
				PlayerWatch arrivals = lobby.capability(Players.class).watch();
				var probe = lobby.capability(FixtureAgentProbeProvider.Probe.class);
				connection.connect();
				connection.connected(Duration.ofSeconds(30));
				arrivals.joined(alice, Duration.ofSeconds(20));
				assertEquals("external:partial", probe.echo("partial"));
				connection.disconnect();
				connection.disconnected();
				context.processes().stop("lobby");
				assertEquals(ProcessState.STOPPED, lobby.state());
				assertSame(gateway, context.processes().get("proxy"));
				var replacement = context.processes().start("lobby");
				assertNotSame(lobby, replacement);
				assertNotEquals(lobby.executionId(), replacement.executionId());
				assertNotNull(lobby.executionId());
				assertEquals(lobby.address(), replacement.address());
				assertEquals("retained", replacement.files().read("partial-marker"));
				connection.rejoin();
				connection.connected(Duration.ofSeconds(30));
				arrivals.joined(alice, Duration.ofSeconds(20));
				assertSame(lobby.capability(Players.class), replacement.capability(Players.class));
				assertEquals("external:replacement", probe.echo("replacement"));
				assertEquals(0, setup.get());
				context.start();
				context.start();
				assertEquals(1, setup.get());
				assertSame(alice, context.players().get("PartialAlice"));
				assertEquals(5, context.processes().all().size());
				assertTrue(context.processes().all().stream().allMatch(process -> process.state() == ProcessState.READY));
				var again = context.players().create(PlayerOptions.builder().name("PartialAliceAgain")
						.login(PlayerLogin.offline("PartialAlice")).connection(PlayerConnection.to("secondary")).build());
				PlayerWatch arrivalsElsewhere = context.processes().server("auxiliary").capability(Players.class).watch();
				PlayerWatch secondary = context.processes().proxy("secondary").capability(Players.class).watch();
				again.capability(Session.class).connect();
				again.capability(Session.class).connected(Duration.ofSeconds(30));
				arrivalsElsewhere.joined(again, Duration.ofSeconds(20));
				secondary.joined(again, "auxiliary", Duration.ofSeconds(20));
				assertEquals("PartialAlice", again.requestedProfile().getUsername());
				assertEquals(alice.requestedProfile().getUniqueId(), again.requestedProfile().getUniqueId());
				assertEquals(Optional.of("lobby"), context.processes().proxy("proxy").capability(Players.class).backend(alice));
				again.destroy();
				var game = context.processes().get("game");
				context.processes().stop("game");
				context.start();
				assertEquals(1, setup.get());
				assertNotSame(game, context.processes().get("game"));
				assertNotEquals(game.executionId(), context.processes().get("game").executionId());
				assertEquals(game.address(), context.processes().get("game").address());
				retained = List.copyOf(context.processes().all());
				successful = true;
			} finally {
				context.finish(successful);
			}
		}
		assertTrue(retained.stream().allMatch(process -> process.state() == ProcessState.STOPPED));
	}
}
