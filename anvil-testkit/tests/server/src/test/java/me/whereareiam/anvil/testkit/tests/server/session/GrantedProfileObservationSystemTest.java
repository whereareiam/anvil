package me.whereareiam.anvil.testkit.tests.server.session;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.server.Server;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.integration.junit.AnvilTest;
import me.whereareiam.anvil.testkit.tests.server.scenario.Paper12111SystemScenario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Observes players by the profile the server granted them at login rather than by the name they asked for.
 */
class GrantedProfileObservationSystemTest {
	@Test
	@AnvilTest(Paper12111SystemScenario.class)
	void observesPlayersWhoseNamesDifferOnlyInLetterCaseAsThemselves(ScenarioContext anvil) {
		SimulatedPlayer lower = join(anvil, "lower", "steve");
		SimulatedPlayer upper = join(anvil, "upper", "Steve");

		PlayerIdentity lowerIdentity = lower.capability(Server.class).joined("server");
		PlayerIdentity upperIdentity = upper.capability(Server.class).joined("server");

		assertObservedAsGranted("steve", lowerIdentity);
		assertObservedAsGranted("Steve", upperIdentity);
		assertNotEquals(lowerIdentity.getObserved().getUniqueId(), upperIdentity.getObserved().getUniqueId());
	}

	private static void assertObservedAsGranted(String username, PlayerIdentity identity) {
		PlayerProfile granted = identity.getGranted();
		assertNotNull(granted);
		assertEquals(username, granted.getUsername());
		assertEquals(granted, identity.getObserved());
	}

	private static SimulatedPlayer join(ScenarioContext anvil, String name, String username) {
		SimulatedPlayer player = anvil.players().create(PlayerOptions.builder()
				.name(name)
				.login(PlayerLogin.offline(username))
				.build());
		Session session = player.capability(Session.class);
		session.connect();
		session.connected();
		return player;
	}
}
