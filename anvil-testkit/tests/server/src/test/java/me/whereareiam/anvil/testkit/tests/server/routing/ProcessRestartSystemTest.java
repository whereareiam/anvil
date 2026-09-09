package me.whereareiam.anvil.testkit.tests.server.routing;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.console.Console;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.testkit.tests.server.extension.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioCatalog;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRestartSystemTest {
	@TestFactory
	Stream<DynamicTest> restartsEverySupportedPlatformAndNativeVersion() {
		ScenarioRegistry registry = new ScenarioRegistry();
		new CompatibilityScenarioCatalog().register(registry);
		String filter = System.getProperty("anvil.matrix.filter", ".*");
		return registry.scenarios().stream().filter(scenario -> scenario.getName().matches(filter))
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
			Server server = alice.capability(Server.class);
			var probe = alice.capability(FixtureAgentProbeProvider.Probe.class);
			String destination = scenario.getProxies().isEmpty() ? "server" : "lobby";
			session.connect();
			session.connected(Duration.ofSeconds(30));
			var identity = server.joined(destination);

			var before = context.processes().get(scenario.getEntrypoint());
			var console = before.capability(Console.class);
			for (var process : context.processes().all())
				if (!process.name().equals(before.name()))
					assertNotSame(console, process.capability(Console.class));
			Files.writeString(before.workDirectory().resolve("restart-marker"), "preserved");
			var after = context.processes().restart(before.name());
			assertNotSame(before, after);
			assertEquals(ProcessState.STOPPED, before.state());
			assertEquals(ProcessState.READY, after.state());
			assertSame(console, after.capability(Console.class));
			assertSame(console, before.capability(Console.class));
			assertEquals(before.address(), after.address());
			assertEquals(before.workDirectory(), after.workDirectory());
			assertEquals("preserved", Files.readString(after.workDirectory().resolve("restart-marker")));
			session.disconnected();
			session.rejoin();
			session.connected(Duration.ofSeconds(30));
			assertEquals(identity.getObservedUniqueId(), server.joined(destination).getObservedUniqueId());
			assertEquals("external:after", probe.echo(destination, "after"));

			if (!scenario.getProxies().isEmpty()) {
				session.disconnect();
				session.disconnected();
				var proxy = context.processes().proxy("proxy");
				var backendConsole = context.processes().server(destination).capability(Console.class);
				var backend = context.processes().restart(destination);
				assertSame(proxy, context.processes().proxy("proxy"));
				assertEquals(ProcessState.READY, proxy.state());
				assertSame(console, proxy.capability(Console.class));
				assertSame(backendConsole, backend.capability(Console.class));
				session.rejoin();
				session.connected(Duration.ofSeconds(30));
				server.joined(destination);
				assertEquals("external:backend", probe.echo(destination, "backend"));
			}
		}
	}
}
