package me.whereareiam.anvil.testkit.tests.server.behavior;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.process.console.ConsoleLine;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ConsoleWatch;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kills a real Velocity proxy under a connected player and brings it back: the proxy runs no shutdown, the player
 * loses its connection, the backends keep running, and the player rejoins the new generation.
 */
class ProcessKillSystemTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(30);
	private static final String CLEAN_SHUTDOWN = "Shutting down the proxy";

	@Test
	void killsAProxyWithoutItsShutdownAndStartsItAgain() throws Exception {
		AnvilScenario original = CompatibilityScenarioFactory.proxyScenario(Platforms.VELOCITY, Platforms.PAPER,
				MinecraftVersion.parse("1.21.11"));
		var proxy = original.getProxies().getFirst().toBuilder().setting("advanced.login-ratelimit", "0").build();
		AnvilScenario scenario = original.toBuilder().name("process-kill").clearProxies().proxy(proxy).build();

		try (var engine = AnvilLauncher.create(EngineOptions.builder().eulaAccepted(true)
				.workDirectory(Path.of("build", "process-kill-live")).build());
		     var context = engine.start(scenario)) {
			boolean successful = false;
			try {
				SimulatedPlayer alice = context.players().create("KilledAlice");
				Session session = alice.capability(Session.class);
				Players lobby = context.processes().server("lobby").capability(Players.class);
				Players proxied = context.processes().proxy("proxy").capability(Players.class);
				PlayerWatch arrivals = lobby.watch();
				PlayerWatch moves = proxied.watch();
				session.connect();
				session.connected(TIMEOUT);
				arrivals.joined(alice, TIMEOUT);
				moves.joined(alice, "lobby", TIMEOUT);
				assertEquals(Optional.of("lobby"), proxied.backend(alice));

				RunningProcess killed = context.processes().get("proxy");
				ConsoleWatch console = killed.console().watch();
				killed.files().write("kill-marker", "retained");
				context.processes().kill("proxy");

				session.disconnected(TIMEOUT);
				assertEquals(DisconnectCause.CONNECTION_LOST, session.state().disconnectCause());
				assertEquals(ProcessState.STOPPED, killed.state());
				List<String> output = console.read(200).getLines().stream().map(ConsoleLine::getText).toList();
				assertFalse(output.stream().anyMatch(line -> line.contains(CLEAN_SHUTDOWN)), output.toString());
				assertTrue(output.getLast().contains("[Anvil] Process 'proxy' was killed on request without a clean shutdown"),
						output.toString());
				assertEquals(ProcessState.READY, context.processes().get("lobby").state());
				arrivals.left(alice, TIMEOUT);
				assertEquals(ProcessState.READY, context.processes().get("game").state());

				RunningProcess replacement = context.processes().start("proxy");
				assertNotEquals(killed.executionId(), replacement.executionId());
				assertEquals(killed.address(), replacement.address());
				assertEquals("retained", replacement.files().read("kill-marker"));
				session.rejoin();
				session.connected(TIMEOUT);
				arrivals.joined(alice, TIMEOUT);
				moves.left(alice, TIMEOUT);
				moves.joined(alice, TIMEOUT);
				moves.joined(alice, "lobby", TIMEOUT);
				assertEquals(Optional.of("lobby"), proxied.backend(alice));
				assertEquals(1, proxied.online().size());
				successful = true;
			} finally {
				context.finish(successful);
			}
		}
	}
}
