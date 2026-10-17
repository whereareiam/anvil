package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import me.whereareiam.anvil.capability.screen.model.Title;
import me.whereareiam.anvil.capability.screen.packet.BossBarPacketListener;
import me.whereareiam.anvil.capability.screen.packet.ScreenPackets;
import me.whereareiam.anvil.capability.screen.packet.TitlePacketListener;
import me.whereareiam.anvil.capability.screen.type.BossBarChange;
import me.whereareiam.anvil.capability.screen.type.BossBarColor;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScreenBindingTest {
	private static final long TICK_NANOS = 50_000_000L;
	private static final UUID EVENT = new UUID(0, 1);
	private static final UUID QUEST = new UUID(0, 2);

	private final AtomicLong clock = new AtomicLong();
	private final RecordingPackets packets = new RecordingPackets();
	private final RecordingPlayer player = new RecordingPlayer();

	@Test
	void showsATitleWithTheSubtitleAndTimesSetBeforeIt() {
		NativeSession session = connect();

		packets.titles(session).times(5, 40, 5);
		packets.titles(session).subtitle("by Alice");
		packets.titles(session).title("Announcement");

		assertEquals(List.of(title("Announcement", "by Alice", 5, 40, 5)), player.emitted(ScreenOperations.TITLE));
	}

	@Test
	void recordsATitleAgainWhenItsSubtitleArrivesWhileItIsShown() {
		NativeSession session = connect();

		packets.titles(session).title("Announcement");
		clock.addAndGet(99 * TICK_NANOS);
		packets.titles(session).subtitle("by Alice");

		assertEquals(List.of(title("Announcement", "", 10, 70, 20), title("Announcement", "by Alice", 10, 70, 20)),
				player.emitted(ScreenOperations.TITLE));
	}

	@Test
	void forgetsTheTitleAndItsSubtitleOnceItsTimesHavePassed() {
		NativeSession session = connect();

		packets.titles(session).subtitle("first subtitle");
		packets.titles(session).title("First");
		clock.addAndGet(100 * TICK_NANOS);
		packets.titles(session).subtitle("second subtitle");
		packets.titles(session).title("Second");

		assertEquals(List.of(title("First", "first subtitle", 10, 70, 20), title("Second", "second subtitle", 10, 70, 20)),
				player.emitted(ScreenOperations.TITLE));
	}

	@Test
	void restartsAShownTitleWithNewTimesAndKeepsTimesANegativeValueLeavesUnchanged() {
		NativeSession session = connect();

		packets.titles(session).title("First");
		clock.addAndGet(90 * TICK_NANOS);
		packets.titles(session).times(-1, 20, -1);
		clock.addAndGet(40 * TICK_NANOS);
		packets.titles(session).subtitle("still shown");

		assertEquals(List.of(title("First", "", 10, 70, 20), title("First", "still shown", 10, 20, 20)),
				player.emitted(ScreenOperations.TITLE));
	}

	@Test
	void clearsTheTitleAndResetsItsTimesOnlyWhenAsked() {
		NativeSession session = connect();

		packets.titles(session).times(1, 2, 3);
		packets.titles(session).title("Cleared");
		packets.titles(session).cleared(false);
		packets.titles(session).subtitle("pending");
		packets.titles(session).title("Kept times");
		packets.titles(session).cleared(true);
		packets.titles(session).title("Default times");

		assertEquals(List.of(
				title("Cleared", "", 1, 2, 3),
				title("Kept times", "pending", 1, 2, 3),
				title("Default times", "", 10, 70, 20)
		), player.emitted(ScreenOperations.TITLE));
	}

	@Test
	void emitsEveryActionBar() {
		NativeSession session = connect();

		packets.actionBars(session).accept("+1 follower");
		packets.actionBars(session).accept("+1 follower");

		assertEquals(List.of("+1 follower", "+1 follower"), player.emitted(ScreenOperations.ACTION_BAR));
	}

	@Test
	void emitsEachBossBarChangeWithTheWholeBar() {
		NativeSession session = connect();
		BossBar added = bar(EVENT, "Event");

		packets.bossBars(session).added(added);
		packets.bossBars(session).progress(EVENT, 0.5F);
		packets.bossBars(session).text(EVENT, "Event ends soon");
		packets.bossBars(session).style(EVENT, BossBarColor.RED, BossBarOverlay.NOTCHED_10);
		packets.bossBars(session).flags(EVENT, true, true, true);
		packets.bossBars(session).progress(QUEST, 0.1F);
		packets.bossBars(session).removed(EVENT);
		packets.bossBars(session).removed(EVENT);

		BossBar halfway = added.toBuilder().progress(0.5F).build();
		BossBar renamed = halfway.toBuilder().text("Event ends soon").build();
		BossBar styled = renamed.toBuilder().color(BossBarColor.RED).overlay(BossBarOverlay.NOTCHED_10).build();
		BossBar flagged = styled.toBuilder().darkensSky(true).playsBossMusic(true).createsFog(true).build();
		assertEquals(List.of(
				new BossBarEvent(BossBarChange.ADDED, added),
				new BossBarEvent(BossBarChange.UPDATED, halfway),
				new BossBarEvent(BossBarChange.UPDATED, renamed),
				new BossBarEvent(BossBarChange.UPDATED, styled),
				new BossBarEvent(BossBarChange.UPDATED, flagged),
				new BossBarEvent(BossBarChange.REMOVED, flagged)
		), player.emitted(ScreenOperations.BOSS_BAR));
	}

	@Test
	void removesTheBarsOfAConnectionWhenItEnds() {
		NativeSession session = connect();
		packets.bossBars(session).added(bar(EVENT, "Event"));
		packets.bossBars(session).added(bar(QUEST, "Quest"));

		packets.bossBars(session).closed();

		assertEquals(List.of(
				new BossBarEvent(BossBarChange.ADDED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.ADDED, bar(QUEST, "Quest")),
				new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.REMOVED, bar(QUEST, "Quest"))
		), player.emitted(ScreenOperations.BOSS_BAR));
	}

	@Test
	void removesTheBarsOfAPreviousConnectionWhoseEndWasNotReportedWhenANewOneStarts() {
		NativeSession first = connect();
		packets.bossBars(first).added(bar(EVENT, "Event"));
		NativeSession second = new NativeSession();

		player.startSession(second);

		assertEquals(List.of(
				new BossBarEvent(BossBarChange.ADDED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event"))
		), player.emitted(ScreenOperations.BOSS_BAR));
	}

	@Test
	void emitsWhatTheCurrentSessionShowsOnlyAndListensToEachNewSession() {
		NativeSession first = connect();
		Consumer<String> firstActionBars = packets.actionBars(first);
		BossBarPacketListener firstBossBars = packets.bossBars(first);
		NativeSession second = new NativeSession();
		player.session = second;

		firstActionBars.accept("late from the first session");
		firstBossBars.closed();
		player.startSession(second);
		packets.actionBars(second).accept("second session");

		assertEquals(List.of("second session"), player.emitted(ScreenOperations.ACTION_BAR));
		assertEquals(List.of(second), List.copyOf(packets.sessions.keySet()));
	}

	@Test
	void removesTheListenerOfTheCurrentSessionWhenClosedAndRegistersNoOperations() {
		RecordingOperations operations = new RecordingOperations();
		WorkerBinding binding = new ScreenBinding<>(packets, clock::get).bind(player, operations);
		player.startSession(new NativeSession());

		binding.close();

		assertEquals(Map.of(), packets.sessions);
		assertEquals(List.of(), operations.registered);
	}

	@Test
	void refusesToListenToASessionOfAnotherType() {
		new ScreenBinding<>(packets, clock::get).bind(player, new RecordingOperations());

		assertThrows(ClassCastException.class, () -> player.startSession("not a native session"));

		assertEquals(Map.of(), packets.sessions);
	}

	private NativeSession connect() {
		new ScreenBinding<>(packets, clock::get).bind(player, new RecordingOperations());
		NativeSession session = new NativeSession();
		player.startSession(session);
		return session;
	}

	private static Title title(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
		return Title.builder().title(title).subtitle(subtitle).fadeInTicks(fadeIn).stayTicks(stay).fadeOutTicks(fadeOut).build();
	}

	private static BossBar bar(UUID id, String text) {
		return BossBar.builder()
				.id(id)
				.text(text)
				.progress(1F)
				.color(BossBarColor.PINK)
				.overlay(BossBarOverlay.PROGRESS)
				.build();
	}

	private static final class NativeSession {
	}

	/**
	 * Keeps the listeners of each session, through which the test delivers packets.
	 */
	private static final class RecordingPackets implements ScreenPackets<NativeSession> {
		private final Map<NativeSession, Listeners> sessions = new LinkedHashMap<>();

		@Override
		public @NotNull Class<NativeSession> sessionType() {
			return NativeSession.class;
		}

		@Override
		public @NotNull Runnable listenTitles(@NotNull NativeSession session, @NotNull TitlePacketListener listener) {
			listeners(session).titles = listener;
			return () -> sessions.remove(session);
		}

		@Override
		public @NotNull Runnable listenActionBars(@NotNull NativeSession session, @NotNull Consumer<String> actionBars) {
			listeners(session).actionBars = actionBars;
			return () -> sessions.remove(session);
		}

		@Override
		public @NotNull Runnable listenBossBars(@NotNull NativeSession session, @NotNull BossBarPacketListener listener) {
			listeners(session).bossBars = listener;
			return () -> sessions.remove(session);
		}

		private TitlePacketListener titles(NativeSession session) {
			return sessions.get(session).titles;
		}

		private Consumer<String> actionBars(NativeSession session) {
			return sessions.get(session).actionBars;
		}

		private BossBarPacketListener bossBars(NativeSession session) {
			return sessions.get(session).bossBars;
		}

		private Listeners listeners(NativeSession session) {
			return sessions.computeIfAbsent(session, ignored -> new Listeners());
		}

		private static final class Listeners {
			private TitlePacketListener titles;
			private Consumer<String> actionBars;
			private BossBarPacketListener bossBars;
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final List<String> registered = new ArrayList<>();

		@Override
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler) {
			registered.add(channelOperation.getId());
		}
	}

	/**
	 * A player whose native session generations the test starts: each {@link #startSession(Object)} closes the listeners
	 * of the previous generation and binds them for the new session, as the worker does before a session connects.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final List<Emitted> emitted = new ArrayList<>();
		private @Nullable Object session;
		private @Nullable Function<Object, Subscription> listener;
		private @Nullable Subscription generation;

		private void startSession(Object nativeSession) {
			if (generation != null) generation.close();
			generation = null;
			session = nativeSession;
			if (listener != null) generation = listener.apply(nativeSession);
		}

		private <E> List<E> emitted(EventDescriptor<E> eventDescriptor) {
			return emitted.stream()
					.filter(event -> event.id().equals(eventDescriptor.getId()))
					.map(event -> eventDescriptor.getPayloadType().cast(event.payload()))
					.toList();
		}

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull UUID uniqueId() {
			return UUID.nameUUIDFromBytes("Alice".getBytes());
		}

		@Override
		public void connect() {
			throw new AssertionError("The screen does not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("The screen does not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("The screen does not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			if (session == null) throw new IllegalStateException("Player Alice is not connected");

			return session;
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			return nativeSession == session;
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			this.listener = listener;
			return () -> {
				this.listener = null;
				if (generation != null) generation.close();
				generation = null;
			};
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("The screen does not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("The screen does not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			emitted.add(new Emitted(eventDescriptor.getId(), payload));
		}

		private record Emitted(String id, Object payload) {
		}
	}
}
