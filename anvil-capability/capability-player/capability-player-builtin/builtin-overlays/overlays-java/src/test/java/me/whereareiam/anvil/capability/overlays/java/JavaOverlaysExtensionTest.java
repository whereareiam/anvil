package me.whereareiam.anvil.capability.overlays.java;

import me.whereareiam.anvil.capability.overlays.channel.OverlaysChannel;
import me.whereareiam.anvil.capability.overlays.model.BossBar;
import me.whereareiam.anvil.capability.overlays.model.BossBarEvent;
import me.whereareiam.anvil.capability.overlays.model.Title;
import me.whereareiam.anvil.capability.overlays.type.BossBarChange;
import me.whereareiam.anvil.capability.overlays.type.BossBarColor;
import me.whereareiam.anvil.capability.overlays.type.BossBarOverlay;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventAdd;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventOperation;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventRemove;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateName;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateProgress;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateProperties;
import me.whereareiam.anvil.edition.java.protocol.model.overlay.BossEventUpdateStyle;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundBossEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundClearTitles;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetActionBarText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetSubtitleText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetTitleText;
import me.whereareiam.anvil.edition.java.protocol.packet.overlay.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.edition.java.protocol.type.overlay.BossEventColor;
import me.whereareiam.anvil.edition.java.protocol.type.overlay.BossEventOverlay;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.model.text.Text;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaOverlaysExtensionTest {
	private static final long TICK_NANOS = 50_000_000L;
	private static final UUID EVENT = new UUID(0, 1);
	private static final UUID QUEST = new UUID(0, 2);

	private final AtomicLong clock = new AtomicLong();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();
	private final JavaOverlaysExtension extension = new JavaOverlaysExtension(clock::get);

	@Test
	void isDiscoveredAsTheEditionWideOverlaysExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaOverlaysExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.overlays", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void showsATitleWithTheSubtitleAndTimesSetBeforeIt() {
		connect();

		player.deliver(new ClientboundSetTitlesAnimation(5, 40, 5));
		player.deliver(new ClientboundSetSubtitleText("by Alice"));
		player.deliver(new ClientboundSetTitleText("Announcement"));

		assertEquals(List.of(title("Announcement", "by Alice", 5, 40, 5)), player.emitted(OverlaysChannel.TITLE));
	}

	@Test
	void recordsATitleAgainWhenItsSubtitleArrivesWhileItIsShown() {
		connect();

		player.deliver(new ClientboundSetTitleText("Announcement"));
		clock.addAndGet(99 * TICK_NANOS);
		player.deliver(new ClientboundSetSubtitleText("by Alice"));

		assertEquals(List.of(title("Announcement", "", 10, 70, 20), title("Announcement", "by Alice", 10, 70, 20)),
				player.emitted(OverlaysChannel.TITLE));
	}

	@Test
	void forgetsTheTitleAndItsSubtitleOnceItsTimesHavePassed() {
		connect();

		player.deliver(new ClientboundSetSubtitleText("first subtitle"));
		player.deliver(new ClientboundSetTitleText("First"));
		clock.addAndGet(100 * TICK_NANOS);
		player.deliver(new ClientboundSetSubtitleText("second subtitle"));
		player.deliver(new ClientboundSetTitleText("Second"));

		assertEquals(List.of(title("First", "first subtitle", 10, 70, 20), title("Second", "second subtitle", 10, 70, 20)),
				player.emitted(OverlaysChannel.TITLE));
	}

	@Test
	void restartsAShownTitleWithNewTimesAndKeepsTimesANegativeValueLeavesUnchanged() {
		connect();

		player.deliver(new ClientboundSetTitleText("First"));
		clock.addAndGet(90 * TICK_NANOS);
		player.deliver(new ClientboundSetTitlesAnimation(-1, 20, -1));
		clock.addAndGet(40 * TICK_NANOS);
		player.deliver(new ClientboundSetSubtitleText("still shown"));

		assertEquals(List.of(title("First", "", 10, 70, 20), title("First", "still shown", 10, 20, 20)),
				player.emitted(OverlaysChannel.TITLE));
	}

	@Test
	void clearsTheTitleAndResetsItsTimesOnlyWhenAsked() {
		connect();

		player.deliver(new ClientboundSetTitlesAnimation(1, 2, 3));
		player.deliver(new ClientboundSetTitleText("Cleared"));
		player.deliver(new ClientboundClearTitles(false));
		player.deliver(new ClientboundSetSubtitleText("pending"));
		player.deliver(new ClientboundSetTitleText("Kept times"));
		player.deliver(new ClientboundClearTitles(true));
		player.deliver(new ClientboundSetTitleText("Default times"));

		assertEquals(List.of(
				title("Cleared", "", 1, 2, 3),
				title("Kept times", "pending", 1, 2, 3),
				title("Default times", "", 10, 70, 20)
		), player.emitted(OverlaysChannel.TITLE));
	}

	@Test
	void startsEachConnectionWithoutATitleAndWithTheDefaultTimes() {
		connect();
		player.deliver(new ClientboundSetTitlesAnimation(1, 2, 3));
		player.deliver(new ClientboundSetTitleText("First connection"));

		player.rejoin();
		player.deliver(new ClientboundSetSubtitleText("second connection"));
		player.deliver(new ClientboundSetTitleText("Second"));

		assertEquals(List.of(title("First connection", "", 1, 2, 3), title("Second", "second connection", 10, 70, 20)),
				player.emitted(OverlaysChannel.TITLE));
	}

	@Test
	void emitsEveryActionBar() {
		connect();

		player.deliver(new ClientboundSetActionBarText("+1 follower"));
		player.deliver(new ClientboundSetActionBarText("+1 follower"));

		assertEquals(List.of("+1 follower", "+1 follower"), player.emitted(OverlaysChannel.ACTION_BAR));
	}

	@Test
	void emitsActionBarTextSentAsChatButNoOtherChat() {
		connect();

		player.deliver(new ClientboundSystemChat(Text.literal("+1 follower"), true));
		player.deliver(new ClientboundSystemChat(Text.literal("Welcome"), false));

		assertEquals(List.of("+1 follower"), player.emitted(OverlaysChannel.ACTION_BAR));
	}

	@Test
	void emitsEachBossBarChangeWithTheWholeBar() {
		connect();
		BossBar added = bar(EVENT, "Event");

		player.deliver(new ClientboundBossEvent(EVENT, add("Event")));
		player.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateProgress(0.5F)));
		player.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateName("Event ends soon")));
		player.deliver(new ClientboundBossEvent(EVENT,
				new BossEventUpdateStyle(BossEventColor.RED, BossEventOverlay.NOTCHED_10)));
		player.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateProperties(true, true, true)));
		player.deliver(new ClientboundBossEvent(QUEST, new BossEventUpdateProgress(0.1F)));
		player.deliver(new ClientboundBossEvent(EVENT, new BossEventRemove()));
		player.deliver(new ClientboundBossEvent(EVENT, new BossEventRemove()));

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
		), player.emitted(OverlaysChannel.BOSS_BAR));
	}

	@Test
	void mapsEveryColorAndOverlayOfTheProtocol() {
		connect();
		player.deliver(new ClientboundBossEvent(EVENT, add("Event")));

		for (BossEventColor color : BossEventColor.values())
			player.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateStyle(color, BossEventOverlay.PROGRESS)));
		for (BossEventOverlay overlay : BossEventOverlay.values())
			player.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateStyle(BossEventColor.PINK, overlay)));

		List<BossBar> updated = player.emitted(OverlaysChannel.BOSS_BAR).stream()
				.filter(event -> event.change() == BossBarChange.UPDATED)
				.map(BossBarEvent::bar)
				.toList();
		assertEquals(List.of(BossBarColor.values()), updated.subList(0, 7).stream().map(BossBar::color).toList());
		assertEquals(List.of(BossBarOverlay.values()), updated.subList(7, 12).stream().map(BossBar::overlay).toList());
	}

	@Test
	void removesTheBarsOfAConnectionWhenItEnds() {
		connect();
		player.deliver(new ClientboundBossEvent(EVENT, add("Event")));
		player.deliver(new ClientboundBossEvent(QUEST, add("Quest")));

		player.disconnect();

		assertEquals(List.of(
				new BossBarEvent(BossBarChange.ADDED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.ADDED, bar(QUEST, "Quest")),
				new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.REMOVED, bar(QUEST, "Quest"))
		), player.emitted(OverlaysChannel.BOSS_BAR));
	}

	@Test
	void removesTheBarsOfAPreviousConnectionWhenANewOneReplacesIt() {
		connect();
		player.deliver(new ClientboundBossEvent(EVENT, add("Event")));

		player.rejoin();
		player.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateProgress(0.5F)));

		assertEquals(List.of(
				new BossBarEvent(BossBarChange.ADDED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event"))
		), player.emitted(OverlaysChannel.BOSS_BAR));
	}

	@Test
	void stopsListeningAndObservingConnectionsWithoutEmittingWhenClosedAndRegistersNoOperations() {
		WorkerBinding binding = extension.bind(player, operations);
		player.connect();
		player.deliver(new ClientboundBossEvent(EVENT, add("Event")));

		binding.close();
		player.rejoin();
		player.deliver(new ClientboundSetActionBarText("late"));

		for (Class<? extends ClientboundPacket> type : List.of(
				ClientboundSetTitlesAnimation.class,
				ClientboundSetTitleText.class,
				ClientboundSetSubtitleText.class,
				ClientboundClearTitles.class,
				ClientboundSetActionBarText.class,
				ClientboundSystemChat.class,
				ClientboundBossEvent.class
		))
			assertFalse(player.protocol().wants(type), type.getSimpleName());

		assertEquals(List.of(new BossBarEvent(BossBarChange.ADDED, bar(EVENT, "Event"))),
				player.emitted(OverlaysChannel.BOSS_BAR));
		assertEquals(List.of(), player.emitted(OverlaysChannel.ACTION_BAR));
		assertEquals(List.of(), operations.registered());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		silent.connect();

		assertEquals(List.of(), silent.emittedEvents());
	}

	private void connect() {
		extension.bind(player, operations);
		player.connect();
	}

	private static Title title(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
		return Title.builder()
				.title(title)
				.subtitle(subtitle)
				.fadeInTicks(fadeIn)
				.stayTicks(stay)
				.fadeOutTicks(fadeOut)
				.build();
	}

	private static BossEventOperation add(String name) {
		return BossEventAdd.builder()
				.name(name)
				.progress(1F)
				.color(BossEventColor.PINK)
				.overlay(BossEventOverlay.PROGRESS)
				.build();
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
}
