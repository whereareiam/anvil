package me.whereareiam.anvil.testkit.tests.server.behavior;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.RemoteEndpoint;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Joins a real Velocity proxy as a remote: one scenario runs the proxy, and another scenario of remotes alone names the
 * proxy's address as a player types it. The player announces the host the vanilla client announces for an IP
 * literal, the name its reverse lookup finds, and the proxy routes it by that host through its forced hosts.
 */
class RemoteEndpointSystemTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	@Test
	void aPlayerJoinsARunningProxyAsARemoteAndIsRoutedByTheHostTheVanillaClientAnnounces() throws Exception {
		// The vanilla client announces getHostName() of the resolved address, which for a literal is its reverse name.
		String announced = InetAddress.getByName("127.0.0.1").getHostName();
		AnvilScenario original = CompatibilityScenarioFactory.proxyScenario(Platforms.VELOCITY, Platforms.PAPER,
				MinecraftVersion.parse("1.21.11"));
		var proxy = original.getProxies().getFirst().toBuilder()
				.setting("advanced.login-ratelimit", "0")
				.setting("forced-hosts.\"" + announced + "\"", "[\"game\"]")
				.build();
		AnvilScenario network = original.toBuilder().name("remote-network").clearProxies().proxy(proxy).build();

		try (var engine = AnvilLauncher.create(EngineOptions.builder().eulaAccepted(true)
				.workDirectory(Path.of("build", "remote-endpoint-live")).build());
		     var running = engine.start(network)) {
			boolean successful = false;
			try {
				InetSocketAddress listener = running.processes().get("proxy").address();
				AnvilScenario visit = AnvilScenario.builder()
						.name("remote-visit")
						.entrypoint("network")
						.remote(RemoteEndpoint.builder()
								.name("network")
								.address(listener.getAddress().getHostAddress() + ":" + listener.getPort())
								.minecraftVersion("1.21.11")
								.build())
						.build();

				try (var visiting = engine.start(visit)) {
					assertTrue(visiting.processes().all().isEmpty());
					PlayerWatch game = running.processes().server("game").capability(Players.class).watch();
					Players gateway = running.processes().proxy("proxy").capability(Players.class);

					SimulatedPlayer visitor = visiting.players().create("Visitor");
					Session session = visitor.capability(Session.class);
					session.connect();
					session.connected(TIMEOUT);
					game.joined(visitor, TIMEOUT);
					assertEquals(Optional.of("game"), gateway.backend(visitor));
					session.disconnect();
					session.disconnected(TIMEOUT);
				}
				successful = true;
			} finally {
				running.finish(successful);
			}
		}
	}
}
