package me.whereareiam.anvil.example.patience.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconnectChallengeTest {
	@Test
	void ignoresPlayersWhoHaveNotVolunteeredForTheCeremony() {
		ReconnectChallenge challenge = new ReconnectChallenge();

		assertEquals(ReconnectChallenge.NOT_STARTED, challenge.reconnect("Alice"));
	}

	@Test
	void acceptsThreeReconnectsAsCompellingProofOfIdentity() {
		ReconnectChallenge challenge = new ReconnectChallenge();
		challenge.start("Alice");

		assertFalse(challenge.provenBy(challenge.reconnect("Alice")));
		assertFalse(challenge.provenBy(challenge.reconnect("Alice")));
		assertTrue(challenge.provenBy(challenge.reconnect("Alice")));
	}
}
