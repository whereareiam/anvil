package me.whereareiam.anvil.testkit.tests.server.routing;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.extension.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRestartSystemTest {
	@TestFactory
	Stream<DynamicTest> restartsEverySupportedPlatformAndNativeVersion() {
		String filter = System.getProperty("anvil.matrix.filter", ".*");
		return CompatibilityScenarioFactory.fixtureScenarios().stream().filter(scenario -> scenario.getName().matches(filter))
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> verify(scenario)));
	}

	private void verify(AnvilScenario original) throws Exception {
		AnvilScenario scenario = original.toBuilder().clearProxies().proxies(original.getProxies().stream()
				.map(proxy -> proxy.toBuilder().setting(proxy.getPlatform().equals(Platforms.VELOCITY)
						? "advanced.login-ratelimit" : "connection_throttle", "0").build()).toList()).build();
		try (ScenarioEngine engine = AnvilLauncher.create(EngineOptions.builder().eulaAccepted(true).build());
		     ScenarioContext context = engine.start(scenario)) {
			var alice = context.players().create("Alice");
			Session session = alice.capability(Session.class);
			String destination = scenario.getProxies().isEmpty() ? "server" : "lobby";
			var probe = context.processes().server(destination).capability(FixtureAgentProbeProvider.Probe.class);
			Players players = context.processes().server(destination).capability(Players.class);
			PlayerWatch arrivals = players.watch();
			session.connect();
			session.connected(Duration.ofSeconds(30));
			var identity = arrivals.joined(alice).getProfile();

			var before = context.processes().get(scenario.getEntrypoint());
			var dispatch = before.capability(Dispatch.class);
			for (var process : context.processes().all())
				if (!process.name().equals(before.name()))
					assertNotSame(dispatch, process.capability(Dispatch.class));
			Files.writeString(before.workDirectory().resolve("restart-marker"), "preserved");
			var after = context.processes().restart(before.name());
			assertNotSame(before, after);
			assertEquals(ProcessState.STOPPED, before.state());
			assertEquals(ProcessState.READY, after.state());
			assertSame(dispatch, after.capability(Dispatch.class));
			assertSame(dispatch, before.capability(Dispatch.class));
			assertEquals(before.address(), after.address());
			assertEquals(before.workDirectory(), after.workDirectory());
			assertEquals("preserved", Files.readString(after.workDirectory().resolve("restart-marker")));
			session.disconnected();
			session.rejoin();
			session.connected(Duration.ofSeconds(30));
			assertEquals(identity.getUniqueId(), arrivals.joined(alice).getProfile().getUniqueId());
			assertSame(players, context.processes().server(destination).capability(Players.class));
			assertEquals("external:after", probe.echo("after"));

			if (!scenario.getProxies().isEmpty()) {
				session.disconnect();
				session.disconnected();
				var proxy = context.processes().proxy("proxy");
				var backendDispatch = context.processes().server(destination).capability(Dispatch.class);
				var backend = context.processes().restart(destination);
				assertSame(proxy, context.processes().proxy("proxy"));
				assertEquals(ProcessState.READY, proxy.state());
				assertSame(dispatch, proxy.capability(Dispatch.class));
				assertSame(backendDispatch, backend.capability(Dispatch.class));
				Players proxied = proxy.capability(Players.class);
				PlayerWatch moves = proxied.watch();
				session.rejoin();
				session.connected(Duration.ofSeconds(30));
				arrivals.joined(alice);
				moves.joined(alice, destination);
				assertEquals(Optional.of(destination), proxied.backend(alice));
				assertEquals("external:backend", probe.echo("backend"));
			}
		}
	}
}
