package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickPace;
import me.whereareiam.anvil.capability.ticks.type.TickPace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static me.whereareiam.anvil.capability.ticks.SimulatedServer.NORMAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenPacesTest {
	private static final AgentTickPace FASTEST = AgentTickPace.builder().rate(20).sprinting(true).build();
	private static final AgentTickPace SLOW = AgentTickPace.builder().rate(5).sprinting(false).build();

	private final SimulatedServer server = new SimulatedServer();
	private final OpenPaces paces = new OpenPaces("server", server);

	@Test
	void setsThePaceAndGivesBackThePaceTheServerRanAtBefore() {
		server.pace = SLOW;

		try (Pacing fast = paces.open(TickPace.fastest())) {
			assertEquals(TickPace.fastest(), fast.pace());
			assertEquals(FASTEST, server.pace);
		}

		assertEquals(SLOW, server.pace);
	}

	@Test
	void setsEachKindOfPaceAsTheTickRateManagerKeepsIt() {
		paces.open(TickPace.rate(5)).close();
		paces.open(TickPace.normal()).close();

		assertEquals(List.of(SLOW, NORMAL, NORMAL, NORMAL), server.paces);
	}

	@Test
	void nestsPacesAndRefusesToCloseAnOuterOneFirst() {
		Pacing slow = paces.open(TickPace.rate(5));
		Pacing fast = paces.open(TickPace.fastest());

		IllegalStateException failure = assertThrows(IllegalStateException.class, slow::close);

		assertEquals("The pace TickPace.Rate(ticksPerSecond=5.0) of 'server' cannot close while the pace "
				+ "TickPace.Fastest() set after it is still open", failure.getMessage());
		fast.close();
		assertEquals(SLOW, server.pace);
		slow.close();
		slow.close();
		assertEquals(NORMAL, server.pace);
		assertEquals(List.of(SLOW, FASTEST, SLOW, NORMAL), server.paces);
	}

	@Test
	void closesThePacesTheScenarioLeftOpenInnermostFirst() {
		paces.open(TickPace.rate(5));
		paces.open(TickPace.fastest());

		paces.closeAll();

		assertEquals(NORMAL, server.pace);
		assertEquals(List.of(SLOW, FASTEST, SLOW, NORMAL), server.paces);
	}

	@Test
	void forgivesAServerThatStoppedWithItsPace() {
		paces.open(TickPace.fastest());
		server.stopped = true;

		paces.closeAll();
		paces.closeAll();
	}

	@Test
	void givesARestartedServerThePaceStillOpen() {
		paces.restarted();
		assertEquals(List.of(), server.paces);

		Pacing fast = paces.open(TickPace.fastest());
		server.restart();
		paces.restarted();

		assertEquals(FASTEST, server.pace);
		fast.close();
		assertEquals(NORMAL, server.pace);
	}
}
