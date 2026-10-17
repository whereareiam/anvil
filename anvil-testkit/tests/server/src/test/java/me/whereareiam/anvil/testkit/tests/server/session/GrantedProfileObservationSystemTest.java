package me.whereareiam.anvil.testkit.tests.server.session;

import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.players.PlayerWatch;
import me.whereareiam.anvil.capability.players.Players;
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
		Players server = anvil.processes().server("server").capability(Players.class);
		PlayerWatch arrivals = server.watch();
		SimulatedPlayer lower = join(anvil, "lower", "steve");
		SimulatedPlayer upper = join(anvil, "upper", "Steve");
		arrivals.joined(lower);
		arrivals.joined(upper);

		PlayerProfile lowerProfile = server.profile(lower).orElseThrow();
		PlayerProfile upperProfile = server.profile(upper).orElseThrow();

		assertObservedAsGranted("steve", lower, lowerProfile);
		assertObservedAsGranted("Steve", upper, upperProfile);
		assertNotEquals(lowerProfile.getUniqueId(), upperProfile.getUniqueId());
	}

	private static void assertObservedAsGranted(String username, SimulatedPlayer player, PlayerProfile observed) {
		PlayerProfile granted = player.grantedProfile();
		assertNotNull(granted);
		assertEquals(username, granted.getUsername());
		assertEquals(granted, observed);
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
