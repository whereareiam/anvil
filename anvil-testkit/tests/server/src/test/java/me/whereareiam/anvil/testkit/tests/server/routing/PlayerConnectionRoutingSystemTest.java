package me.whereareiam.anvil.testkit.tests.server.routing;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Connects players through a real Velocity proxy and checks what the proxy sees of their connection: the host they
 * announce selects a backend through Velocity's forced hosts, and the address they connect from appears in the
 * proxy's connection log.
 */
class PlayerConnectionRoutingSystemTest {
	private static final String FORCED_HOST = "games.anvil.test";
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	@Test
	void velocitySeesTheAnnouncedVirtualHostAndTheChosenSourceAddress() {
		AnvilScenario original = CompatibilityScenarioFactory.fixtureScenarios().stream()
				.filter(scenario -> scenario.getName().equals("velocity-paper-1.21.11"))
				.findFirst().orElseThrow();
		var proxy = original.getProxies().getFirst().toBuilder()
				.setting("advanced.login-ratelimit", "0")
				.setting("enable-player-address-logging", "true")
				.setting("forced-hosts.\"" + FORCED_HOST + "\"", "[\"game\"]")
				.build();
		AnvilScenario scenario = original.toBuilder().name("player-connection-routing").clearProxies().proxy(proxy).build();

		try (var engine = AnvilLauncher.create(EngineOptions.builder().eulaAccepted(true)
				.workDirectory(Path.of("build", "player-connection-live")).build());
		     var context = engine.start(scenario)) {
			boolean successful = false;
			try {
				ProcessConsole console = context.processes().get("proxy").console();
				long checkpoint = console.checkpoint();

				SimulatedPlayer routed = context.players().create(PlayerOptions.builder()
						.name("Routed")
						.connection(PlayerConnection.builder().virtualHost(FORCED_HOST).sourceAddress("127.0.0.2").build())
						.build());
				join(routed);
				assertEquals("game", routed.capability(Server.class).joined("game", TIMEOUT).getRoute().getServer());
				console.await("[connected player] Routed (/127.0.0.2:", checkpoint, TIMEOUT);

				SimulatedPlayer direct = context.players().create("Direct");
				join(direct);
				assertEquals("lobby", direct.capability(Server.class).joined("lobby", TIMEOUT).getRoute().getServer());
				console.await("[connected player] Direct (/127.0.0.1:", checkpoint, TIMEOUT);
				successful = true;
			} finally {
				context.finish(successful);
			}
		}
	}

	private static void join(SimulatedPlayer player) {
		Session session = player.capability(Session.class);
		session.connect();
		session.connected(TIMEOUT);
	}
}
