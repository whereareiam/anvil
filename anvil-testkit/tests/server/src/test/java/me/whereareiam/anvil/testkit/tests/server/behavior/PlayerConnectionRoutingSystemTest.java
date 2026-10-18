package me.whereareiam.anvil.testkit.tests.server.behavior;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

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
		AnvilScenario original = CompatibilityScenarioFactory.proxyScenario(Platforms.VELOCITY, Platforms.PAPER,
				MinecraftVersion.parse("1.21.11"));
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
				ConsoleWatch console = context.processes().get("proxy").console().watch();

				SimulatedPlayer routed = context.players().create(PlayerOptions.builder()
						.name("Routed")
						.connection(PlayerConnection.builder().virtualHost(FORCED_HOST).sourceAddress("127.0.0.2").build())
						.build());
				PlayerWatch game = context.processes().server("game").capability(Players.class).watch();
				PlayerWatch lobby = context.processes().server("lobby").capability(Players.class).watch();
				Players gateway = context.processes().proxy("proxy").capability(Players.class);
				PlayerWatch moves = gateway.watch();
				join(routed);
				game.joined(routed, TIMEOUT);
				moves.joined(routed, "game", TIMEOUT);
				assertEquals(Optional.of("game"), gateway.backend(routed));
				console.printed("[connected player] Routed (/127.0.0.2:", TIMEOUT);

				SimulatedPlayer direct = context.players().create("Direct");
				join(direct);
				lobby.joined(direct, TIMEOUT);
				moves.joined(direct, "lobby", TIMEOUT);
				lobby.neverJoined(routed, Duration.ofSeconds(1));
				moves.neverJoined(routed, "lobby", Duration.ofSeconds(1));
				assertEquals(Optional.of("lobby"), gateway.backend(direct));
				console.printed("[connected player] Direct (/127.0.0.1:", TIMEOUT);
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
