package me.whereareiam.anvil.testkit.tests.server.routing;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.lifecycle.ProcessTimeouts;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.extension.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PartialScenarioLifecycleSystemTest {
	@Test
	void controlsIndividualProcessesThenCompletesTwoProxyThreeServerEnvironment() throws Exception {
		AnvilScenario original = CompatibilityScenarioFactory.fixtureScenarios().stream()
				.filter(scenario -> scenario.getName().equals("velocity-paper-1.21.11"))
				.findFirst().orElseThrow();
		var auxiliary = original.getServers().getFirst().toBuilder().name("auxiliary").build();
		var proxy = original.getProxies().getFirst().toBuilder()
				.server("auxiliary").setting("advanced.login-ratelimit", "0").build();
		AtomicInteger setup = new AtomicInteger();
		AnvilScenario network = original.toBuilder().name("partial-network")
				.processTimeouts(ProcessTimeouts.builder().startup(Duration.ofMinutes(2)).build())
				.server(auxiliary).clearProxies().proxy(proxy).proxy(proxy.toBuilder().name("secondary").build())
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
				Files.writeString(lobby.workDirectory().resolve("partial-marker"), "retained");
				var alice = context.players().create("PartialAlice");
				Session connection = alice.capability(Session.class);
				Server observation = alice.capability(Server.class);
				var probe = alice.capability(FixtureAgentProbeProvider.Probe.class);
				connection.connect();
				connection.connected(Duration.ofSeconds(30));
				observation.joined("lobby", Duration.ofSeconds(20));
				assertEquals("external:partial", probe.echo("lobby", "partial"));
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
				assertEquals("retained", Files.readString(replacement.workDirectory().resolve("partial-marker")));
				connection.rejoin();
				connection.connected(Duration.ofSeconds(30));
				observation.joined("lobby", Duration.ofSeconds(20));
				assertEquals("external:replacement", probe.echo("lobby", "replacement"));
				assertEquals(0, setup.get());
				context.start();
				context.start();
				assertEquals(1, setup.get());
				assertSame(alice, context.players().get("PartialAlice"));
				assertEquals(5, context.processes().all().size());
				assertTrue(context.processes().all().stream().allMatch(process -> process.state() == ProcessState.READY));
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
