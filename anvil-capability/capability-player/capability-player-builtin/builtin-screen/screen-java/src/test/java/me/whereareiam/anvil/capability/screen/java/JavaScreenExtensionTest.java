package me.whereareiam.anvil.capability.screen.java;

import me.whereareiam.anvil.capability.screen.channel.ScreenChannel;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import me.whereareiam.anvil.capability.screen.model.Title;
import me.whereareiam.anvil.capability.screen.type.BossBarChange;
import me.whereareiam.anvil.capability.screen.type.BossBarColor;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventAdd;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventOperation;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventRemove;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateName;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProgress;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProperties;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateStyle;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundBossEvent;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetActionBarText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitlesAnimation;
import me.whereareiam.anvil.protocol.java.api.type.BossEventColor;
import me.whereareiam.anvil.protocol.java.api.type.BossEventOverlay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaScreenExtensionTest {
	private static final long TICK_NANOS = 50_000_000L;
	private static final UUID EVENT = new UUID(0, 1);
	private static final UUID QUEST = new UUID(0, 2);

	private final AtomicLong clock = new AtomicLong();
	private final RecordingProtocol protocol = new RecordingProtocol();
	private final RecordingPlayer player = new RecordingPlayer(protocol);
	private final JavaScreenExtension extension = new JavaScreenExtension(clock::get);

	@Test
	void isDiscoveredAsTheEditionWideScreenExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaScreenExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.screen", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void showsATitleWithTheSubtitleAndTimesSetBeforeIt() {
		connect();

		protocol.deliver(new ClientboundSetTitlesAnimation(5, 40, 5));
		protocol.deliver(new ClientboundSetSubtitleText("by Alice"));
		protocol.deliver(new ClientboundSetTitleText("Announcement"));

		assertEquals(List.of(title("Announcement", "by Alice", 5, 40, 5)), player.emitted(ScreenChannel.TITLE));
	}

	@Test
	void recordsATitleAgainWhenItsSubtitleArrivesWhileItIsShown() {
		connect();

		protocol.deliver(new ClientboundSetTitleText("Announcement"));
		clock.addAndGet(99 * TICK_NANOS);
		protocol.deliver(new ClientboundSetSubtitleText("by Alice"));

		assertEquals(List.of(title("Announcement", "", 10, 70, 20), title("Announcement", "by Alice", 10, 70, 20)),
				player.emitted(ScreenChannel.TITLE));
	}

	@Test
	void forgetsTheTitleAndItsSubtitleOnceItsTimesHavePassed() {
		connect();

		protocol.deliver(new ClientboundSetSubtitleText("first subtitle"));
		protocol.deliver(new ClientboundSetTitleText("First"));
		clock.addAndGet(100 * TICK_NANOS);
		protocol.deliver(new ClientboundSetSubtitleText("second subtitle"));
		protocol.deliver(new ClientboundSetTitleText("Second"));

		assertEquals(List.of(title("First", "first subtitle", 10, 70, 20), title("Second", "second subtitle", 10, 70, 20)),
				player.emitted(ScreenChannel.TITLE));
	}

	@Test
	void restartsAShownTitleWithNewTimesAndKeepsTimesANegativeValueLeavesUnchanged() {
		connect();

		protocol.deliver(new ClientboundSetTitleText("First"));
		clock.addAndGet(90 * TICK_NANOS);
		protocol.deliver(new ClientboundSetTitlesAnimation(-1, 20, -1));
		clock.addAndGet(40 * TICK_NANOS);
		protocol.deliver(new ClientboundSetSubtitleText("still shown"));

		assertEquals(List.of(title("First", "", 10, 70, 20), title("First", "still shown", 10, 20, 20)),
				player.emitted(ScreenChannel.TITLE));
	}

	@Test
	void clearsTheTitleAndResetsItsTimesOnlyWhenAsked() {
		connect();

		protocol.deliver(new ClientboundSetTitlesAnimation(1, 2, 3));
		protocol.deliver(new ClientboundSetTitleText("Cleared"));
		protocol.deliver(new ClientboundClearTitles(false));
		protocol.deliver(new ClientboundSetSubtitleText("pending"));
		protocol.deliver(new ClientboundSetTitleText("Kept times"));
		protocol.deliver(new ClientboundClearTitles(true));
		protocol.deliver(new ClientboundSetTitleText("Default times"));

		assertEquals(List.of(
				title("Cleared", "", 1, 2, 3),
				title("Kept times", "pending", 1, 2, 3),
				title("Default times", "", 10, 70, 20)
		), player.emitted(ScreenChannel.TITLE));
	}

	@Test
	void startsEachConnectionWithoutATitleAndWithTheDefaultTimes() {
		connect();
		protocol.deliver(new ClientboundSetTitlesAnimation(1, 2, 3));
		protocol.deliver(new ClientboundSetTitleText("First connection"));

		player.startConnection();
		protocol.deliver(new ClientboundSetSubtitleText("second connection"));
		protocol.deliver(new ClientboundSetTitleText("Second"));

		assertEquals(List.of(title("First connection", "", 1, 2, 3), title("Second", "second connection", 10, 70, 20)),
				player.emitted(ScreenChannel.TITLE));
	}

	@Test
	void emitsEveryActionBar() {
		connect();

		protocol.deliver(new ClientboundSetActionBarText("+1 follower"));
		protocol.deliver(new ClientboundSetActionBarText("+1 follower"));

		assertEquals(List.of("+1 follower", "+1 follower"), player.emitted(ScreenChannel.ACTION_BAR));
	}

	@Test
	void emitsEachBossBarChangeWithTheWholeBar() {
		connect();
		BossBar added = bar(EVENT, "Event");

		protocol.deliver(new ClientboundBossEvent(EVENT, add("Event")));
		protocol.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateProgress(0.5F)));
		protocol.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateName("Event ends soon")));
		protocol.deliver(new ClientboundBossEvent(EVENT,
				new BossEventUpdateStyle(BossEventColor.RED, BossEventOverlay.NOTCHED_10)));
		protocol.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateProperties(true, true, true)));
		protocol.deliver(new ClientboundBossEvent(QUEST, new BossEventUpdateProgress(0.1F)));
		protocol.deliver(new ClientboundBossEvent(EVENT, new BossEventRemove()));
		protocol.deliver(new ClientboundBossEvent(EVENT, new BossEventRemove()));

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
		), player.emitted(ScreenChannel.BOSS_BAR));
	}

	@Test
	void mapsEveryColorAndOverlayOfTheProtocol() {
		connect();
		protocol.deliver(new ClientboundBossEvent(EVENT, add("Event")));

		for (BossEventColor color : BossEventColor.values())
			protocol.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateStyle(color, BossEventOverlay.PROGRESS)));
		for (BossEventOverlay overlay : BossEventOverlay.values())
			protocol.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateStyle(BossEventColor.PINK, overlay)));

		List<BossBar> updated = player.emitted(ScreenChannel.BOSS_BAR).stream()
				.filter(event -> event.getChange() == BossBarChange.UPDATED)
				.map(BossBarEvent::getBar)
				.toList();
		assertEquals(List.of(BossBarColor.values()), updated.subList(0, 7).stream().map(BossBar::getColor).toList());
		assertEquals(List.of(BossBarOverlay.values()), updated.subList(7, 12).stream().map(BossBar::getOverlay).toList());
	}

	@Test
	void removesTheBarsOfAConnectionWhenItEnds() {
		connect();
		protocol.deliver(new ClientboundBossEvent(EVENT, add("Event")));
		protocol.deliver(new ClientboundBossEvent(QUEST, add("Quest")));

		player.endConnection();

		assertEquals(List.of(
				new BossBarEvent(BossBarChange.ADDED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.ADDED, bar(QUEST, "Quest")),
				new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.REMOVED, bar(QUEST, "Quest"))
		), player.emitted(ScreenChannel.BOSS_BAR));
	}

	@Test
	void removesTheBarsOfAPreviousConnectionWhenANewOneReplacesIt() {
		connect();
		protocol.deliver(new ClientboundBossEvent(EVENT, add("Event")));

		player.startConnection();
		protocol.deliver(new ClientboundBossEvent(EVENT, new BossEventUpdateProgress(0.5F)));

		assertEquals(List.of(
				new BossBarEvent(BossBarChange.ADDED, bar(EVENT, "Event")),
				new BossBarEvent(BossBarChange.REMOVED, bar(EVENT, "Event"))
		), player.emitted(ScreenChannel.BOSS_BAR));
	}

	@Test
	void stopsListeningAndObservingConnectionsWithoutEmittingWhenClosedAndRegistersNoOperations() {
		RecordingOperations operations = new RecordingOperations();
		WorkerBinding binding = extension.bind(player, operations);
		player.startConnection();
		protocol.deliver(new ClientboundBossEvent(EVENT, add("Event")));

		binding.close();
		player.startConnection();
		protocol.deliver(new ClientboundSetActionBarText("late"));

		assertEquals(Map.of(), protocol.listeners);
		assertNull(player.connections);
		assertEquals(List.of(new BossBarEvent(BossBarChange.ADDED, bar(EVENT, "Event"))),
				player.emitted(ScreenChannel.BOSS_BAR));
		assertEquals(List.of(), player.emitted(ScreenChannel.ACTION_BAR));
		assertEquals(List.of(), operations.registered);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer silent = new RecordingPlayer(null);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, new RecordingOperations()));
		assertNull(silent.connections);
	}

	private void connect() {
		extension.bind(player, new RecordingOperations());
		player.startConnection();
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

	/**
	 * Hands delivered packets to the listener of their class; the screen sends nothing.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			listeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> listeners.remove(type);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			throw new AssertionError("The screen sends no packets");
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The screen binding observes no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return true;
		}

		private void deliver(ClientboundPacket packet) {
			Consumer<Object> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final List<String> registered = new ArrayList<>();

		@Override
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			registered.add(operation.getId());
		}
	}

	/**
	 * A player that speaks the given protocol, or none, whose connections the test starts and ends: each
	 * {@link #startConnection()} closes the binding of the previous connection and binds the new one, as the worker
	 * does before a new session connects, and {@link #endConnection()} closes the current one, as the worker does when
	 * the connection ends.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<Emitted> emitted = new ArrayList<>();
		private @Nullable Function<Object, Subscription> connections;
		private @Nullable Subscription connection;

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
		}

		private void startConnection() {
			endConnection();
			if (connections != null) connection = connections.apply(new Object());
		}

		private void endConnection() {
			if (connection != null) connection.close();
			connection = null;
		}

		private <E> List<E> emitted(EventDescriptor<E> event) {
			return emitted.stream()
					.filter(emitted -> emitted.id().equals(event.getId()))
					.map(emitted -> event.getPayloadType().cast(emitted.payload()))
					.toList();
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			if (protocol == null) return PlayerBindingContext.super.protocol(type);

			return type.cast(protocol);
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
			throw new AssertionError("The screen uses the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("The screen uses the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			connections = listener;
			return () -> {
				connections = null;
				endConnection();
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
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			emitted.add(new Emitted(event.getId(), payload));
		}

		private record Emitted(String id, Object payload) {
		}
	}
}
