package me.whereareiam.anvil.example.patience.journey;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.example.patience.scenario.Paper12111Scenario;
import me.whereareiam.anvil.example.patience.scenario.Paper2612Scenario;
import me.whereareiam.anvil.junit.AnvilTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Replaces the traditional release checklist item “reconnect until the plugin develops trust.”
 */
class ProofOfPatienceJourneyTest {
	private static final UUID AUTHENTICATED_UUID = UUID.fromString("00000000-0000-4000-8000-000000000042");
	private static final String CHALLENGE_STARTED = "proof-of-patience:started";
	private static final String TRY_AGAIN = "proof-of-patience:try-again:";

	@Test
	@AnvilTest(Paper12111Scenario.class)
	void aliceProvesHerPatienceOnPaper12111(ScenarioContext anvil) {
		verifyAliceProvesHerPatience(anvil);
	}

	@Test
	@AnvilTest(Paper2612Scenario.class)
	void aliceProvesHerPatienceOnPaper2612(ScenarioContext anvil) {
		verifyAliceProvesHerPatience(anvil);
	}

	private void verifyAliceProvesHerPatience(ScenarioContext anvil) {
		assertEquals(ProcessState.READY, anvil.processes().server("authentication").state());

		SimulatedPlayer alice = anvil.players().create("Alice");
		Session session = alice.capability(Session.class);
		Messages messages = alice.capability(Messages.class);
		Server server = alice.capability(Server.class);
		session.connect();
		session.connected();

		PlayerIdentity initialIdentity = server.joined("authentication");
		UUID initialUuid = initialIdentity.getObservedUuid();
		assertNotNull(initialUuid);
		assertNotEquals(AUTHENTICATED_UUID, initialUuid);

		messages.command("/auth");
		assertEquals(CHALLENGE_STARTED, session.kicked());
		assertFalse(session.state().connected());

		for (int attempt = 1; attempt <= 2; attempt++) {
			session.rejoin();
			assertEquals(TRY_AGAIN + attempt, session.kicked());
			assertFalse(session.state().connected());
		}

		session.rejoin();
		session.connected();
		PlayerIdentity authenticatedIdentity = server.joined("authentication");
		assertEquals("authentication", authenticatedIdentity.getServer());
		assertEquals(AUTHENTICATED_UUID, authenticatedIdentity.getObservedUuid());
		assertNotEquals(initialUuid, authenticatedIdentity.getObservedUuid());

		PlayerIdentity observedByPlugin = server.identity();
		assertNotNull(observedByPlugin);
		assertEquals(AUTHENTICATED_UUID, observedByPlugin.getObservedUuid());

		alice.destroy();
	}
}
