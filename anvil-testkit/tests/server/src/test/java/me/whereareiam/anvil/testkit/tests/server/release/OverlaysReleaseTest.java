package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.overlays.BossBarWatch;
import me.whereareiam.anvil.capability.overlays.Overlays;
import me.whereareiam.anvil.capability.overlays.TitleWatch;
import me.whereareiam.anvil.capability.overlays.model.BossBar;
import me.whereareiam.anvil.capability.overlays.model.Title;
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
class OverlaysReleaseTest {
	@Test
	void seesATitleWithItsSubtitleAndTimes(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		TitleWatch titles = player.capability(Overlays.class).watchTitles();

		server.console("title " + player.name() + " times 5 40 5");
		server.console("title " + player.name() + " subtitle \"anvil:subtitle\"");
		server.console("title " + player.name() + " title \"anvil:title\"");

		Title title = titles.shown(shown -> shown.title().equals("anvil:title")
				&& shown.subtitle().equals("anvil:subtitle"), Duration.ofSeconds(10));
		assertEquals(40, title.stayTicks());
	}

	@Test
	void seesAnActionBar(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		var actionBars = player.capability(Overlays.class).watchActionBars();

		server.console("title " + player.name() + " actionbar \"anvil:action-bar\"");

		actionBars.shown("anvil:action-bar", Duration.ofSeconds(10));
	}

	@Test
	void seesABossBarWithItsProgressUntilItIsRemoved(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		Overlays overlays = player.capability(Overlays.class);
		BossBarWatch bossBars = overlays.watchBossBars();
		String id = "anvil:" + player.name().toLowerCase();

		server.console("bossbar add " + id + " \"anvil:boss-bar\"");
		server.console("bossbar set " + id + " players " + player.name());
		server.console("bossbar set " + id + " value 50");
		BossBar bar = bossBars.shown(shown -> shown.text().equals("anvil:boss-bar") && shown.progress() == 0.5F,
				Duration.ofSeconds(10));
		assertEquals(List.of(bar), overlays.bossBars());
		server.console("bossbar remove " + id);

		bossBars.removed(removed -> removed.id().equals(bar.id()), Duration.ofSeconds(10));
		assertTrue(overlays.bossBars().isEmpty());
	}
}
