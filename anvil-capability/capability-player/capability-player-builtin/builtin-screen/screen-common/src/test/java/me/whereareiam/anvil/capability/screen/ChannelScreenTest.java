package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import me.whereareiam.anvil.capability.screen.model.Title;
import me.whereareiam.anvil.capability.screen.type.BossBarChange;
import me.whereareiam.anvil.capability.screen.type.BossBarColor;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelScreenTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final UUID EVENT = new UUID(0, 1);
	private static final UUID QUEST = new UUID(0, 2);

	private final RecordingChannel channel = new RecordingChannel();
	private final ChannelScreen screen = new ChannelScreen(channel);

	@Test
	void keepsEachHistoryInOrder() {
		assertTrue(screen.titles().isEmpty());

		channel.publish(ScreenOperations.TITLE, title("Welcome", ""));
		channel.publish(ScreenOperations.ACTION_BAR, "+1 follower");
		channel.publish(ScreenOperations.BOSS_BAR, added(bar(EVENT, "Event", 1F)));
		channel.publish(ScreenOperations.TITLE, title("Welcome", "Alice"));

		assertEquals(List.of(title("Welcome", ""), title("Welcome", "Alice")), screen.titles());
		assertEquals(List.of("+1 follower"), screen.actionBars());
		assertEquals(List.of(added(bar(EVENT, "Event", 1F))), screen.bossBarEvents());
		assertThrows(UnsupportedOperationException.class, () -> screen.actionBars().add("changed"));
	}

	@Test
	void listsTheBossBarsShownNowInTheOrderTheyWereAdded() {
		channel.publish(ScreenOperations.BOSS_BAR, added(bar(EVENT, "Event", 1F)));
		channel.publish(ScreenOperations.BOSS_BAR, added(bar(QUEST, "Quest", 0F)));
		channel.publish(ScreenOperations.BOSS_BAR, new BossBarEvent(BossBarChange.UPDATED, bar(EVENT, "Event", 0.5F)));
		assertEquals(List.of(bar(EVENT, "Event", 0.5F), bar(QUEST, "Quest", 0F)), screen.bossBars());

		channel.publish(ScreenOperations.BOSS_BAR, new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event", 0.5F)));
		assertEquals(List.of(bar(QUEST, "Quest", 0F)), screen.bossBars());
	}

	@Test
	void watchesTitlesSinceItByTitleOrSubtitleAndContinuesAfterEachMatch() {
		channel.publish(ScreenOperations.TITLE, title("Announcement", "by Bob"));
		TitleWatch titles = screen.watchTitles();

		assertEquals("Player did not see a matching title", assertThrows(IllegalStateException.class,
				() -> titles.shown("Announcement", TIMEOUT)).getMessage());

		channel.publish(ScreenOperations.TITLE, title("Announcement", ""));
		channel.publish(ScreenOperations.TITLE, title("Announcement", "by Alice"));
		assertEquals(title("Announcement", "by Alice"), titles.shown("Alice", TIMEOUT));
		assertEquals(List.of(), titles.unread());
		assertThrows(IllegalStateException.class, () -> titles.shown(title -> title.getTitle().equals("Announcement"), TIMEOUT));
	}

	@Test
	void failsWhenAForbiddenTitleIsShownSinceThePreviousWaitNamingBothLines() {
		TitleWatch titles = screen.watchTitles();
		titles.neverShown("Error", TIMEOUT);

		channel.publish(ScreenOperations.TITLE, title("Error", "try again"));

		assertEquals("Player saw a title it must not see: Error / try again", assertThrows(IllegalStateException.class,
				() -> titles.neverShown("try", TIMEOUT)).getMessage());
	}

	@Test
	void watchesActionBarsAndExpectsOneAmongOtherOutcomes() {
		channel.publish(ScreenOperations.ACTION_BAR, "+1 follower");
		ActionBarWatch actionBars = screen.watchActionBars();
		Expectation follower = actionBars.expectShown(text -> text.contains("follower"));
		assertFalse(follower.met());
		assertEquals("see a matching action bar", follower.getDescription());

		channel.publish(ScreenOperations.ACTION_BAR, "+1 follower");
		channel.publish(ScreenOperations.ACTION_BAR, "-1 follower");

		assertTrue(follower.met());
		assertEquals(List.of("+1 follower", "-1 follower"), actionBars.unread());
		assertEquals("+1 follower", actionBars.shown("follower", TIMEOUT));
		assertEquals("Player saw an action bar it must not see: -1 follower", assertThrows(IllegalStateException.class,
				() -> actionBars.neverShown("-1", TIMEOUT)).getMessage());
	}

	@Test
	void findsTheBossBarChangeThatReachesAStateAndItsRemoval() {
		BossBarWatch bars = screen.watchBossBars();
		channel.publish(ScreenOperations.BOSS_BAR, added(bar(EVENT, "Event", 0F)));
		channel.publish(ScreenOperations.BOSS_BAR, new BossBarEvent(BossBarChange.UPDATED, bar(EVENT, "Event", 0.25F)));
		channel.publish(ScreenOperations.BOSS_BAR, new BossBarEvent(BossBarChange.UPDATED, bar(EVENT, "Event", 0.75F)));
		Expectation removed = bars.expectRemoved(bar -> bar.getId().equals(EVENT));
		assertFalse(removed.met());

		assertEquals(bar(EVENT, "Event", 0.75F), bars.shown(bar -> bar.getProgress() >= 0.5F, TIMEOUT));
		assertEquals("Player did not see a matching boss bar change", assertThrows(IllegalStateException.class,
				() -> bars.removed("Event", TIMEOUT)).getMessage());

		channel.publish(ScreenOperations.BOSS_BAR, new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event", 0.75F)));
		assertTrue(removed.met());
		bars.neverShown("Event", TIMEOUT);
		assertEquals(bar(EVENT, "Event", 0.75F), bars.removed("Event", TIMEOUT));
		assertEquals(List.of(), bars.unread());
	}

	@Test
	void failsWhenAForbiddenBossBarIsShownNamingItsText() {
		BossBarWatch bars = screen.watchBossBars();

		channel.publish(ScreenOperations.BOSS_BAR, added(bar(QUEST, "Maintenance soon", 1F)));

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

	/**
	 * Delivers published events synchronously; a wait whose condition does not hold fails immediately instead of
	 * timing out.
	 */
	private static final class RecordingChannel implements ProtocolChannel {
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
			throw new AssertionError("The screen sends no requests");
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(ScreenProvider.ID);
		}
	}
}
