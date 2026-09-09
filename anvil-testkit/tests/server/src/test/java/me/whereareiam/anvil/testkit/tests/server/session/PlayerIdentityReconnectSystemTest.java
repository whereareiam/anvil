package me.whereareiam.anvil.testkit.tests.server.session;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.junit.AnvilTest;
import me.whereareiam.anvil.testkit.tests.server.scenario.Paper12111SystemScenario;
import me.whereareiam.anvil.testkit.tests.server.scenario.Paper2612SystemScenario;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayerIdentityReconnectSystemTest {
	private static final UUID AUTHENTICATED_UUID = UUID.fromString("00000000-0000-4000-8000-000000000042");
	private static final String AUTH_STARTED = "anvil:auth:started";
	private static final String REJOIN_FORBIDDEN = "anvil:auth:rejoin-forbidden:";

	@Test
	@AnvilTest(Paper12111SystemScenario.class)
	void observesKickRejoinAndIdentityReplacementOnPaper12111(ScenarioContext anvil) {
		verify(anvil);
	}

	@Test
	@AnvilTest(Paper2612SystemScenario.class)
	void observesKickRejoinAndIdentityReplacementOnPaper2612(ScenarioContext anvil) {
		verify(anvil);
	}

	private void verify(ScenarioContext anvil) {
		SimulatedPlayer alice = anvil.players().create("Alice");
		Session session = alice.capability(Session.class);
		Messages messages = alice.capability(Messages.class);
		Server server = alice.capability(Server.class);
		session.connect();
		session.connected();

		UUID initialUuid = server.joined("server").getObservedUuid();
		assertNotNull(initialUuid);
		assertNotEquals(AUTHENTICATED_UUID, initialUuid);

		messages.command("/auth");
		assertEquals(AUTH_STARTED, session.kicked());
		assertFalse(session.state().connected());

		for (int attempt = 1; attempt <= 2; attempt++) {
			session.rejoin();
			assertEquals(REJOIN_FORBIDDEN + attempt, session.kicked());
			assertFalse(session.state().connected());
		}

		session.rejoin();
		session.connected();
		PlayerIdentity authenticated = server.joined("server");
		assertEquals(AUTHENTICATED_UUID, authenticated.getObservedUuid());
		assertNotEquals(initialUuid, authenticated.getObservedUuid());

		PlayerIdentity observedByServer = server.identity();
		assertNotNull(observedByServer);
		assertEquals(AUTHENTICATED_UUID, observedByServer.getObservedUuid());
	}
}
