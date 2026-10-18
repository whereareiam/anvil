package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.dispatch.Dispatch;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureAgentProbeProvider;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Restarts the release's server and checks that the player reconnects to it with the same identity, and that the
 * server keeps its address, workspace and capabilities.
 */
@Release
class RestartReleaseTest {
	@Test
	void keepsTheServersAddressWorkspaceAndCapabilitiesAcrossARestart(ScenarioContext anvil) throws Exception {
		Players players = anvil.processes().server("server").capability(Players.class);
		PlayerWatch arrivals = players.watch();
		SimulatedPlayer player = new FixtureServer(anvil).join();
		Session session = player.capability(Session.class);
		PlayerProfile identity = arrivals.joined(player, Duration.ofSeconds(10)).getProfile();
		RunningProcess before = anvil.processes().server("server");
		Dispatch dispatch = before.capability(Dispatch.class);
		Files.writeString(before.workDirectory().resolve("restart-marker"), "preserved");

		RunningProcess after = anvil.processes().restart(before.name());

		assertNotSame(before, after);
		assertEquals(ProcessState.STOPPED, before.state());
		assertEquals(ProcessState.READY, after.state());
		assertSame(dispatch, after.capability(Dispatch.class));
		assertEquals(before.address(), after.address());
		assertEquals(before.workDirectory(), after.workDirectory());
		assertEquals("preserved", Files.readString(after.workDirectory().resolve("restart-marker")));
		session.disconnected();
		session.rejoin();
		session.connected(Duration.ofSeconds(30));
		PlayerProfile rejoined = arrivals.joined(player, Duration.ofSeconds(10)).getProfile();
		assertEquals(identity.getUniqueId(), rejoined.getUniqueId());
		assertSame(players, anvil.processes().server("server").capability(Players.class));
		assertEquals("external:after", after.capability(FixtureAgentProbeProvider.Probe.class).echo("after"));
	}
}
