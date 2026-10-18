package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.cookies.CookieWatch;
import me.whereareiam.anvil.capability.cookies.Cookies;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Holds the cookies the server stores and answers its requests for them, through the fixture's console command.
 */
@Release
class CookiesReleaseTest {
	/**
	 * The release that added cookies.
	 */
	private static final String COOKIES = "1.20.5";

	@Test
	void holdsACookieTheServerStoresAndAnswersItsRequest(ScenarioContext anvil) {
		assumeTrue(TestedRelease.isAtLeast(COOKIES), "Cookies arrived in " + COOKIES);
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Cookies cookies = player.capability(Cookies.class);
		Messages messages = player.capability(Messages.class);
		CookieWatch asked = cookies.watch();

		server.fixture("anvil-cookie", player, "store anvil:ticket day-pass");
		messages.received("anvil:cookie-stored:anvil:ticket", Duration.ofSeconds(10));
		byte[] stored = cookies.stored("anvil:ticket", Duration.ofSeconds(10));
		assertEquals("day-pass", new String(stored, StandardCharsets.UTF_8));
		server.fixture("anvil-cookie", player, "request anvil:ticket");

		messages.received("anvil:cookie:anvil:ticket:day-pass", Duration.ofSeconds(10));
		CookieRequest request = asked.requested("anvil:ticket", Duration.ofSeconds(10));
		assertEquals("day-pass", new String(request.getAnswer(), StandardCharsets.UTF_8));
		asked.neverRequested("anvil:unasked", Duration.ofSeconds(1));
	}

	@Test
	void answersWithACookieItWasGivenUntilItIsTakenAway(ScenarioContext anvil) {
		assumeTrue(TestedRelease.isAtLeast(COOKIES), "Cookies arrived in " + COOKIES);
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Cookies cookies = player.capability(Cookies.class);
		Messages messages = player.capability(Messages.class);

		cookies.store("anvil:gift", "ribbon".getBytes(StandardCharsets.UTF_8));
		server.fixture("anvil-cookie", player, "request anvil:gift");
		messages.received("anvil:cookie:anvil:gift:ribbon", Duration.ofSeconds(10));
		cookies.remove("anvil:gift");
		server.fixture("anvil-cookie", player, "request anvil:gift");

		messages.received("anvil:cookie:anvil:gift:none", Duration.ofSeconds(10));
	}

	@Test
	void startsANewConnectionWithOnlyTheCookiesGivenWhileDisconnected(ScenarioContext anvil) {
		assumeTrue(TestedRelease.isAtLeast(COOKIES), "Cookies arrived in " + COOKIES);
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Cookies cookies = player.capability(Cookies.class);
		Messages messages = player.capability(Messages.class);
		Session session = player.capability(Session.class);
		server.fixture("anvil-cookie", player, "store anvil:ticket day-pass");
		messages.received("anvil:cookie-stored:anvil:ticket", Duration.ofSeconds(10));

		messages.command("anvil-fixture kick");
		session.kicked(Duration.ofSeconds(10));
		cookies.store("anvil:arrival", "carried".getBytes(StandardCharsets.UTF_8));
		session.rejoin();
		session.connected(Duration.ofSeconds(20));

		server.fixture("anvil-cookie", player, "request anvil:ticket");
		messages.received("anvil:cookie:anvil:ticket:none", Duration.ofSeconds(10));
		server.fixture("anvil-cookie", player, "request anvil:arrival");
		messages.received("anvil:cookie:anvil:arrival:carried", Duration.ofSeconds(10));
		assertEquals(List.of("anvil:arrival"), List.copyOf(cookies.stored().keySet()));
	}

	@Test
	void hasNoCookiesBeforeTheyExisted(ScenarioContext anvil) {
		assumeFalse(TestedRelease.isAtLeast(COOKIES), "Cookies exist since " + COOKIES);

		assertFalse(new FixtureServer(anvil).join().hasCapability(Cookies.class));
	}
}
