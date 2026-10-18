package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.screen.BossBarWatch;
import me.whereareiam.anvil.capability.screen.Screen;
import me.whereareiam.anvil.capability.screen.TitleWatch;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.Title;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sees the titles, action bars and boss bars the server's vanilla commands show the player.
 */
@Release
class ScreenReleaseTest {
	@Test
	void seesATitleWithItsSubtitleAndTimes(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		TitleWatch titles = player.capability(Screen.class).watchTitles();

		server.console("title " + player.name() + " times 5 40 5");
		server.console("title " + player.name() + " subtitle \"anvil:subtitle\"");
		server.console("title " + player.name() + " title \"anvil:title\"");

		Title title = titles.shown(shown -> shown.getTitle().equals("anvil:title")
				&& shown.getSubtitle().equals("anvil:subtitle"), Duration.ofSeconds(10));
		assertEquals(40, title.getStayTicks());
	}

	@Test
	void seesAnActionBar(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		var actionBars = player.capability(Screen.class).watchActionBars();

		server.console("title " + player.name() + " actionbar \"anvil:action-bar\"");

		actionBars.shown("anvil:action-bar", Duration.ofSeconds(10));
	}

	@Test
	void seesABossBarWithItsProgressUntilItIsRemoved(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Screen screen = player.capability(Screen.class);
		BossBarWatch bossBars = screen.watchBossBars();
		String id = "anvil:" + player.name().toLowerCase();

		server.console("bossbar add " + id + " \"anvil:boss-bar\"");
		server.console("bossbar set " + id + " players " + player.name());
		server.console("bossbar set " + id + " value 50");
		BossBar bar = bossBars.shown(shown -> shown.getText().equals("anvil:boss-bar") && shown.getProgress() == 0.5F,
				Duration.ofSeconds(10));
		assertEquals(List.of(bar), screen.bossBars());
		server.console("bossbar remove " + id);

		bossBars.removed(removed -> removed.getId().equals(bar.getId()), Duration.ofSeconds(10));
		assertTrue(screen.bossBars().isEmpty());
	}
}
