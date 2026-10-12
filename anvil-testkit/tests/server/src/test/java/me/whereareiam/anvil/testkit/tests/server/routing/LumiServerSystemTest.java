package me.whereareiam.anvil.testkit.tests.server.routing;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.type.network.ListenerTransport;
import me.whereareiam.anvil.capability.console.Console;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts each verified Lumi server, drives it through its console and the Lumi agent, and connects a Bedrock player
 * whose identity the Lumi agent observes.
 */
@Tag("compatibility")
class LumiServerSystemTest {
	private static final Pattern PROTOCOLS = Pattern.compile("min-protocol: (\\d+)\\s+max-protocol: (\\d+)");

	@TestFactory
	Stream<DynamicTest> runsEveryLumiVersion() {
		String filter = System.getProperty("anvil.matrix.filter", ".*");
		return CompatibilityScenarioFactory.lumiScenarios().stream()
				.filter(scenario -> scenario.getName().matches(filter))
				.map(scenario -> DynamicTest.dynamicTest(scenario.getName(), () -> verify(scenario)));
	}

	private void verify(AnvilScenario scenario) throws IOException {
		EngineOptions options = EngineOptions.builder().eulaAccepted(true).build();
		try (ScenarioEngine engine = AnvilLauncher.create(options); var context = engine.start(scenario)) {
			RunningProcess server = context.processes().server("server");
			Console console = server.capability(Console.class);
			assertEquals(ListenerTransport.UDP, server.transport());

			long before = server.console().checkpoint();
			assertTrue(console.execute("say anvil:lumi-agent"));
			server.console().await("anvil:lumi-agent", before, Duration.ofSeconds(10));
			assertFalse(console.execute("anvil-unknown-command"));

			before = server.console().checkpoint();
			server.console().sendCommand("say anvil:lumi-stdin");
			server.console().await("anvil:lumi-stdin", before, Duration.ofSeconds(10));

			// Lumi rewrites its settings on start; the protocol Anvil allowed must survive that.
			String settings = Files.readString(server.workDirectory().resolve("settings.yml"));
			Matcher protocols = PROTOCOLS.matcher(settings);
			assertTrue(protocols.find(), settings);
			assertEquals(protocols.group(1), protocols.group(2), "Lumi accepts exactly one protocol");

			observesBedrockPlayer(context.players().create("Alice"));
		}
	}

	/**
	 * Lumi lists an offline player to clients under the UUID its login named, but keeps a UUID of its own derived
	 * from the name. The player is granted the listed UUID, and the Lumi agent finds it by that UUID and reports
	 * Lumi's own.
	 */
	private void observesBedrockPlayer(SimulatedPlayer alice) {
		Session session = alice.capability(Session.class);
		Server server = alice.capability(Server.class);

		session.connect();
		session.connected(Duration.ofSeconds(30));
		PlayerIdentity identity = server.joined("server", Duration.ofSeconds(10));

		PlayerProfile granted = identity.getGranted();
		PlayerProfile observed = identity.getObserved();
		assertNotNull(granted, identity.toString());
		assertNotNull(observed, identity.toString());
		assertEquals(identity.getRequested(), granted);
		assertEquals("Alice", observed.getUsername());
		assertEquals(UUID.nameUUIDFromBytes("Alice".getBytes(StandardCharsets.UTF_8)), observed.getUniqueId());
		assertNotEquals(granted.getUniqueId(), observed.getUniqueId());

		session.disconnect();
		session.disconnected(Duration.ofSeconds(10));
		assertNull(server.identity().getGranted());
	}
}
