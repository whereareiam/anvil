package me.whereareiam.anvil.capability.screen;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import me.whereareiam.anvil.capability.screen.model.Title;
import me.whereareiam.anvil.capability.screen.type.BossBarChange;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Screen capability driven over a player's typed channel: every title, action-bar and boss bar event the worker
 * emits is kept in the player's histories, across reconnects. The channel releases the event subscriptions together
 * with the player.
 */
final class ChannelScreen implements Screen {
	private final CapabilityChannel channel;
	private final CopyOnWriteArrayList<Title> titles = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<String> actionBars = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<BossBarEvent> bossBarEvents = new CopyOnWriteArrayList<>();

	ChannelScreen(@NotNull CapabilityChannel channel) {
		this.channel = channel;
		channel.subscribe(ScreenOperations.TITLE, titles::add);
		channel.subscribe(ScreenOperations.ACTION_BAR, actionBars::add);
		channel.subscribe(ScreenOperations.BOSS_BAR, bossBarEvents::add);
	}

	@Override
	public @NotNull List<Title> titles() {
		return List.copyOf(titles);
	}

	@Override
	public @NotNull List<String> actionBars() {
		return List.copyOf(actionBars);
	}

	@Override
	public @NotNull List<BossBarEvent> bossBarEvents() {
		return List.copyOf(bossBarEvents);
	}

	@Override
	public @NotNull List<BossBar> bossBars() {
		Map<UUID, BossBar> shown = new LinkedHashMap<>();
		for (BossBarEvent event : bossBarEvents) {
			if (event.getChange() == BossBarChange.REMOVED) shown.remove(event.getBar().getId());
			else shown.put(event.getBar().getId(), event.getBar());
		}
		return List.copyOf(shown.values());
	}

	@Override
	public @NotNull TitleWatch watchTitles() {
		return new Titles(new Follower<>(this::titles, "see a matching title"));
	}

	@Override
	public @NotNull ActionBarWatch watchActionBars() {
		return new ActionBars(new Follower<>(this::actionBars, "see a matching action bar"));
	}

	@Override
	public @NotNull BossBarWatch watchBossBars() {
		return new BossBars(new Follower<>(this::bossBarEvents, "see a matching boss bar change"));
	}

	/**
	 * Follows one of this player's histories through a cursor and waits through the player's channel.
	 */
	private final class Follower<T> {
		private final HistoryCursor<T> cursor;
		private final String description;

		private Follower(Supplier<List<T>> history, String description) {
			this.cursor = new HistoryCursor<>(history);
			this.description = description;
		}

		private T next(Predicate<T> matcher, Duration timeout) {
			channel.await(() -> cursor.find(matcher) != null, description, timeout);
			return Objects.requireNonNull(cursor.take(matcher));
		}

		private void never(Predicate<T> matcher, Duration duration, String failure, Function<T, String> name) {
			try {
				channel.await(() -> cursor.find(matcher) != null, description, duration);
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			throw new IllegalStateException(failure + name.apply(Objects.requireNonNull(cursor.find(matcher))));
		}

		private Expectation expect(Predicate<T> matcher) {
			return Expectation.of(description, () -> cursor.find(matcher) != null);
		}

		private List<T> unread() {
			return cursor.unread();
		}
	}

	@RequiredArgsConstructor
	private static final class Titles implements TitleWatch {
		private final Follower<Title> follower;

		@Override
		public @NotNull Title shown(@NotNull Predicate<Title> matcher, @NotNull Duration timeout) {
			return follower.next(matcher, timeout);
		}

		@Override
		public void neverShown(@NotNull Predicate<Title> matcher, @NotNull Duration duration) {
			follower.never(matcher, duration, "Player saw a title it must not see: ",
					title -> title.getSubtitle().isEmpty() ? title.getTitle() : title.getTitle() + " / " + title.getSubtitle());
		}

		@Override
		public @NotNull Expectation expectShown(@NotNull Predicate<Title> matcher) {
			return follower.expect(matcher);
		}

		@Override
		public @NotNull List<Title> unread() {
			return follower.unread();
		}
	}

	@RequiredArgsConstructor
	private static final class ActionBars implements ActionBarWatch {
		private final Follower<String> follower;

		@Override
		public @NotNull String shown(@NotNull Predicate<String> matcher, @NotNull Duration timeout) {
			return follower.next(matcher, timeout);
		}

		@Override
		public void neverShown(@NotNull Predicate<String> matcher, @NotNull Duration duration) {
			follower.never(matcher, duration, "Player saw an action bar it must not see: ", Function.identity());
		}

		@Override
		public @NotNull Expectation expectShown(@NotNull Predicate<String> matcher) {
			return follower.expect(matcher);
		}

		@Override
		public @NotNull List<String> unread() {
			return follower.unread();
		}
	}

	@RequiredArgsConstructor
	private static final class BossBars implements BossBarWatch {
		private final Follower<BossBarEvent> follower;

		@Override
		public @NotNull BossBar shown(@NotNull Predicate<BossBar> matcher, @NotNull Duration timeout) {
			return follower.next(shownMatching(matcher), timeout).getBar();
		}

		@Override
		public @NotNull BossBar removed(@NotNull Predicate<BossBar> matcher, @NotNull Duration timeout) {
			return follower.next(removedMatching(matcher), timeout).getBar();
		}

		@Override
		public void neverShown(@NotNull Predicate<BossBar> matcher, @NotNull Duration duration) {
			follower.never(shownMatching(matcher), duration, "Player saw a boss bar it must not see: ", event -> event.getBar().getText());
		}

		@Override
		public @NotNull Expectation expectShown(@NotNull Predicate<BossBar> matcher) {
			return follower.expect(shownMatching(matcher));
		}

		@Override
		public @NotNull Expectation expectRemoved(@NotNull Predicate<BossBar> matcher) {
			return follower.expect(removedMatching(matcher));
		}

		@Override
		public @NotNull List<BossBarEvent> unread() {
			return follower.unread();
		}

		private static Predicate<BossBarEvent> shownMatching(Predicate<BossBar> matcher) {
			return event -> event.getChange() != BossBarChange.REMOVED && matcher.test(event.getBar());
		}

		private static Predicate<BossBarEvent> removedMatching(Predicate<BossBar> matcher) {
			return event -> event.getChange() == BossBarChange.REMOVED && matcher.test(event.getBar());
		}
	}
}
