package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks what the release's server knows and does through its agent: its console, an agent extension, and the
 * players that join it.
 */
@Release
class ServerReleaseTest {
	@Test
	void runsConsoleCommandsAndAgentExtensions(ScenarioContext anvil) {
		RunningServer server = anvil.processes().server("server");

		assertTrue(server.capability(Dispatch.class).execute("list"));
		assertEquals("external:release", server.capability(FixtureAgentProbeProvider.Probe.class).echo("release"));
	}

	@Test
	void seesAPlayerJoinWithTheProfileItsLoginGranted(ScenarioContext anvil) {
		Players players = anvil.processes().server("server").capability(Players.class);
		PlayerWatch arrivals = players.watch();

		SimulatedPlayer player = new FixtureServer(anvil).join();

		assertEquals(player.name(), arrivals.joined(player, Duration.ofSeconds(10)).getProfile().getUsername());
		assertEquals(player.name(), players.profile(player).orElseThrow().getUsername());
	}
}
