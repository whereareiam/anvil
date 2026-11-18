package me.whereareiam.anvil.capability.overlays;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.overlays.channel.OverlaysChannel;
import me.whereareiam.anvil.capability.overlays.model.BossBar;
import me.whereareiam.anvil.capability.overlays.model.BossBarEvent;
import me.whereareiam.anvil.capability.overlays.model.Title;
import me.whereareiam.anvil.capability.overlays.type.BossBarChange;
import me.whereareiam.anvil.capability.overlays.type.BossBarColor;
import me.whereareiam.anvil.capability.overlays.type.BossBarOverlay;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelOverlaysTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final UUID EVENT = new UUID(0, 1);
	private static final UUID QUEST = new UUID(0, 2);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(OverlaysChannel.ID));
	private final ChannelOverlays overlays = new ChannelOverlays(channel);

	@Test
	void keepsEachHistoryInOrder() {
		assertTrue(overlays.titles().isEmpty());

		channel.publish(OverlaysChannel.TITLE, title("Welcome", ""));
		channel.publish(OverlaysChannel.ACTION_BAR, "+1 follower");
		channel.publish(OverlaysChannel.BOSS_BAR, added(bar(EVENT, "Event", 1F)));
		channel.publish(OverlaysChannel.TITLE, title("Welcome", "Alice"));

		assertEquals(List.of(title("Welcome", ""), title("Welcome", "Alice")), overlays.titles());
		assertEquals(List.of("+1 follower"), overlays.actionBars());
		assertEquals(List.of(added(bar(EVENT, "Event", 1F))), overlays.bossBarEvents());
		assertThrows(UnsupportedOperationException.class, () -> overlays.actionBars().add("changed"));
	}

	@Test
	void listsTheBossBarsShownNowInTheOrderTheyWereAdded() {
		channel.publish(OverlaysChannel.BOSS_BAR, added(bar(EVENT, "Event", 1F)));
		channel.publish(OverlaysChannel.BOSS_BAR, added(bar(QUEST, "Quest", 0F)));
		channel.publish(OverlaysChannel.BOSS_BAR, new BossBarEvent(BossBarChange.UPDATED, bar(EVENT, "Event", 0.5F)));
		assertEquals(List.of(bar(EVENT, "Event", 0.5F), bar(QUEST, "Quest", 0F)), overlays.bossBars());

		channel.publish(OverlaysChannel.BOSS_BAR, new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event", 0.5F)));
		assertEquals(List.of(bar(QUEST, "Quest", 0F)), overlays.bossBars());
	}

	@Test
	void watchesTitlesSinceItByTitleOrSubtitleAndContinuesAfterEachMatch() {
		channel.publish(OverlaysChannel.TITLE, title("Announcement", "by Bob"));
		TitleWatch titles = overlays.watchTitles();

		assertEquals("Player did not see a matching title", assertThrows(IllegalStateException.class,
				() -> titles.shown("Announcement", TIMEOUT)).getMessage());

		channel.publish(OverlaysChannel.TITLE, title("Announcement", ""));
		channel.publish(OverlaysChannel.TITLE, title("Announcement", "by Alice"));
		assertEquals(title("Announcement", "by Alice"), titles.shown("Alice", TIMEOUT));
		assertEquals(List.of(), titles.unread());
		assertThrows(IllegalStateException.class, () -> titles.shown(title -> title.getTitle().equals("Announcement"), TIMEOUT));
	}

	@Test
	void failsWhenAForbiddenTitleIsShownSinceThePreviousWaitNamingBothLines() {
		TitleWatch titles = overlays.watchTitles();
		titles.neverShown("Error", TIMEOUT);

		channel.publish(OverlaysChannel.TITLE, title("Error", "try again"));

		assertEquals("Player saw a title it must not see: Error / try again", assertThrows(IllegalStateException.class,
				() -> titles.neverShown("try", TIMEOUT)).getMessage());
	}

	@Test
	void watchesActionBarsAndExpectsOneAmongOtherOutcomes() {
		channel.publish(OverlaysChannel.ACTION_BAR, "+1 follower");
		ActionBarWatch actionBars = overlays.watchActionBars();
		Expectation follower = actionBars.expectShown(text -> text.contains("follower"));
		assertFalse(follower.met());
		assertEquals("see a matching action bar", follower.getDescription());

		channel.publish(OverlaysChannel.ACTION_BAR, "+1 follower");
		channel.publish(OverlaysChannel.ACTION_BAR, "-1 follower");

		assertTrue(follower.met());
		assertEquals(List.of("+1 follower", "-1 follower"), actionBars.unread());
		assertEquals("+1 follower", actionBars.shown("follower", TIMEOUT));
		assertEquals("Player saw an action bar it must not see: -1 follower", assertThrows(IllegalStateException.class,
				() -> actionBars.neverShown("-1", TIMEOUT)).getMessage());
	}

	@Test
	void findsTheBossBarChangeThatReachesAStateAndItsRemoval() {
		BossBarWatch bars = overlays.watchBossBars();
		channel.publish(OverlaysChannel.BOSS_BAR, added(bar(EVENT, "Event", 0F)));
		channel.publish(OverlaysChannel.BOSS_BAR, new BossBarEvent(BossBarChange.UPDATED, bar(EVENT, "Event", 0.25F)));
		channel.publish(OverlaysChannel.BOSS_BAR, new BossBarEvent(BossBarChange.UPDATED, bar(EVENT, "Event", 0.75F)));
		Expectation removed = bars.expectRemoved(bar -> bar.getId().equals(EVENT));
		assertFalse(removed.met());

		assertEquals(bar(EVENT, "Event", 0.75F), bars.shown(bar -> bar.getProgress() >= 0.5F, TIMEOUT));
		assertEquals("Player did not see a matching boss bar change", assertThrows(IllegalStateException.class,
				() -> bars.removed("Event", TIMEOUT)).getMessage());

		channel.publish(OverlaysChannel.BOSS_BAR, new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event", 0.75F)));
		assertTrue(removed.met());
		bars.neverShown("Event", TIMEOUT);
		assertEquals(bar(EVENT, "Event", 0.75F), bars.removed("Event", TIMEOUT));
		assertEquals(List.of(), bars.unread());
	}

	@Test
	void failsWhenAForbiddenBossBarIsShownNamingItsText() {
		BossBarWatch bars = overlays.watchBossBars();

		channel.publish(OverlaysChannel.BOSS_BAR, added(bar(QUEST, "Maintenance soon", 1F)));

		assertEquals("Player saw a boss bar it must not see: Maintenance soon", assertThrows(IllegalStateException.class,
				() -> bars.neverShown("Maintenance", TIMEOUT)).getMessage());
	}

	private static Title title(String title, String subtitle) {
		return Title.builder().title(title).subtitle(subtitle).fadeInTicks(10).stayTicks(70).fadeOutTicks(20).build();
	}

	private static BossBarEvent added(BossBar bar) {
		return new BossBarEvent(BossBarChange.ADDED, bar);
	}

	private static BossBar bar(UUID id, String text, float progress) {
		return BossBar.builder()
				.id(id)
				.text(text)
				.progress(progress)
				.color(BossBarColor.YELLOW)
				.overlay(BossBarOverlay.PROGRESS)
				.build();
	}
}
