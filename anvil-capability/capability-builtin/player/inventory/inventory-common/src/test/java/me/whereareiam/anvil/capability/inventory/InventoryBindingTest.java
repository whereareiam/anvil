package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryBindingTest {
	private static final InventoryItem DIAMONDS = item(4, 3);

	@Test
	void selectsTheHotbarSlotThroughThePortWithTheCastSession() {
		Fixture fixture = new Fixture(new NativeSession());

		assertNull(fixture.operations.execute(InventoryOperations.SELECT, new SlotSelection(3)));

		assertEquals(List.of("inventory.select", "inventory.click"), List.copyOf(fixture.operations.handlers.keySet()));
		assertEquals(List.of(3), fixture.packets.selected);
		assertSame(fixture.player.session, fixture.packets.sessions.getLast());
	}

	@Test
	void clicksTheObservedContainerWithItsStateAndANewActionIdEachTime() {
		Fixture fixture = new Fixture(new NativeSession());
		fixture.packets.listener().contents(1, 7, List.of(DIAMONDS));

		fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0));
		fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(5, InventoryClick.RIGHT, 0));
		fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 8));

		assertEquals(List.of(
				click(1, 7, 1, 4, InventoryClick.LEFT, 0, DIAMONDS),
				click(1, 7, 2, 5, InventoryClick.RIGHT, 0, null),
				click(1, 7, 3, 4, InventoryClick.HOTBAR_SWAP, 8, null)
		), fixture.packets.clicks);
		assertSame(fixture.player.session, fixture.packets.sessions.getLast());
	}

	@Test
	void refusesAHotbarButtonOutsideTheHotbarBeforeSending() {
		Fixture fixture = new Fixture(new NativeSession());

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 9)));

		assertEquals("Invalid hotbar button: 9", failure.getMessage());
		assertTrue(fixture.packets.clicks.isEmpty());
	}

	@Test
	void sendsTheOffHandSwapButtonAndRefusesTheButtonAfterIt() {
		Fixture fixture = new Fixture(new NativeSession());

		fixture.operations.execute(InventoryOperations.CLICK,
				new InventoryAction(4, InventoryClick.HOTBAR_SWAP, ContainerClick.OFF_HAND_BUTTON));
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 41)));

		assertEquals("Invalid hotbar button: 41", failure.getMessage());
		assertEquals(List.of(click(0, 0, 1, 4, InventoryClick.HOTBAR_SWAP, 40, null)), fixture.packets.clicks);
	}

	@Test
	void wrapsTheActionIdBackToOneAfterTheLargestShort() {
		Fixture fixture = new Fixture(new NativeSession());

		for (int click = 0; click <= Short.MAX_VALUE; click++)
			fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0));

		List<ContainerClick> clicks = fixture.packets.clicks;
		assertEquals(Short.MAX_VALUE, clicks.get(clicks.size() - 2).getActionId());
		assertEquals(1, clicks.getLast().getActionId());
	}

	@Test
	void refusesASessionOfAnotherTypeWithoutSendingOrUsingAnActionId() {
		Fixture fixture = new Fixture(new NativeSession());
		fixture.player.session = "not a native session";

		assertThrows(ClassCastException.class, () -> fixture.operations.execute(InventoryOperations.SELECT, new SlotSelection(0)));
		assertThrows(ClassCastException.class,
				() -> fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0)));

		fixture.player.session = new NativeSession();
		fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0));
		assertTrue(fixture.packets.selected.isEmpty());
		assertEquals(1, fixture.packets.clicks.getFirst().getActionId());
	}

	@Test
	void sendsNothingWhileThePlayerIsDisconnected() {
		Fixture fixture = new Fixture(new NativeSession());
		fixture.player.session = null;

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> fixture.operations.execute(InventoryOperations.SELECT, new SlotSelection(0)));
		assertThrows(IllegalStateException.class,
				() -> fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0)));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertTrue(fixture.packets.selected.isEmpty());
		assertTrue(fixture.packets.clicks.isEmpty());
	}

	@Test
	void publishesEveryChangeOfTheObservedContainer() {
		Fixture fixture = new Fixture(new NativeSession());
		InventoryPacketListener listener = fixture.packets.listener();
		InventoryItem stone = item(0, 64);

		listener.opened(2);
		listener.contents(2, 3, List.of(DIAMONDS, stone));
		listener.slot(2, 4, 0, null);
		listener.slot(2, 5, 1, item(1, 1));

		assertEquals(List.of(
				snapshot(2, 0),
				snapshot(2, 3, stone, DIAMONDS),
				snapshot(2, 4, DIAMONDS),
				snapshot(2, 5, item(1, 1), DIAMONDS)
		), fixture.player.snapshots);
	}

	@Test
	void ignoresSlotsOfOtherContainersAndTheCursor() {
		Fixture fixture = new Fixture(new NativeSession());
		InventoryPacketListener listener = fixture.packets.listener();
		listener.contents(2, 3, List.of(DIAMONDS));

		listener.slot(0, 9, 36, item(36, 1));
		listener.slot(-1, 9, -1, item(-1, 1));
		listener.playerSlot(0, item(0, 1));

		assertEquals(List.of(snapshot(2, 3, DIAMONDS)), fixture.player.snapshots);
		fixture.operations.execute(InventoryOperations.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0));
		assertEquals(click(2, 3, 1, 4, InventoryClick.LEFT, 0, DIAMONDS), fixture.packets.clicks.getFirst());
	}

	@Test
	void placesPlayerInventorySlotsInThePlayersInventoryContainer() {
		Fixture fixture = new Fixture(new NativeSession());
		InventoryPacketListener listener = fixture.packets.listener();
		listener.contents(0, 1, List.of());

		listener.playerSlot(0, item(0, 1));
		listener.playerSlot(9, item(9, 2));
		listener.playerSlot(39, item(39, 3));
		listener.playerSlot(40, item(40, 4));
		listener.playerSlot(41, item(41, 5));
		listener.playerSlot(0, null);

		assertEquals(List.of(
				snapshot(0, 1),
				snapshot(0, 1, item(36, 1)),
				snapshot(0, 1, item(9, 2), item(36, 1)),
				snapshot(0, 1, item(5, 3), item(9, 2), item(36, 1)),
				snapshot(0, 1, item(5, 3), item(9, 2), item(36, 1), item(45, 4)),
				snapshot(0, 1, item(5, 3), item(9, 2), item(45, 4))
		), fixture.player.snapshots);
	}

	@Test
	void ignoresPacketsOfAReplacedSessionAndDetachesTheCurrentListenerOnClose() {
		NativeSession first = new NativeSession();
		Fixture fixture = new Fixture(first);
		InventoryPacketListener replaced = fixture.packets.listener();
		NativeSession next = new NativeSession();
		fixture.player.reconnect(next);
		InventoryPacketListener current = fixture.packets.listener();

		replaced.opened(5);
		current.opened(6);
		fixture.binding.close();

		assertEquals(List.of(snapshot(6, 0)), fixture.player.snapshots);
		assertEquals(List.of(first, next), fixture.packets.sessions);
		assertEquals(2, fixture.packets.detached);
	}

	private static InventoryItem item(int slot, int amount) {
		return InventoryItem.builder().slot(slot).protocolId(100 + amount).amount(amount).build();
	}

	private static InventorySnapshot snapshot(int containerId, int stateId, InventoryItem... items) {
		return InventorySnapshot.builder().containerId(containerId).stateId(stateId).items(List.of(items)).build();
	}

	private static ContainerClick click(
			int containerId,
			int stateId,
			int actionId,
			int slot,
			InventoryClick click,
			int button,
			@Nullable InventoryItem clickedItem
	) {
		return ContainerClick.builder()
				.containerId(containerId)
				.stateId(stateId)
				.actionId(actionId)
				.slot(slot)
				.click(click)
				.button(button)
				.clickedItem(clickedItem)
				.build();
	}

	/**
	 * Binds inventory for one player whose first native session is already starting.
	 */
	private static final class Fixture {
		private final RecordingPackets packets = new RecordingPackets();
		private final RecordingPlayer player;
		private final RecordingOperations operations = new RecordingOperations();
		private final WorkerBinding binding;

		private Fixture(Object session) {
			player = new RecordingPlayer(session);
			binding = new InventoryBinding<>(packets).bind(player, operations);
		}
	}

	private static final class NativeSession {
	}

	private static final class RecordingPackets implements InventoryPackets<NativeSession> {
		private final List<NativeSession> sessions = new ArrayList<>();
		private final List<Integer> selected = new ArrayList<>();
		private final List<ContainerClick> clicks = new ArrayList<>();
		private final List<InventoryPacketListener> listeners = new ArrayList<>();
		private int detached;

		@Override
		public @NotNull Class<NativeSession> sessionType() {
			return NativeSession.class;
		}

		@Override
		public void selectHotbar(@NotNull NativeSession session, int slot) {
			sessions.add(session);
			selected.add(slot);
		}

		@Override
		public void click(@NotNull NativeSession session, @NotNull ContainerClick click) {
			sessions.add(session);
			clicks.add(click);
		}

		@Override
		public @NotNull Runnable listen(@NotNull NativeSession session, @NotNull InventoryPacketListener listener) {
			sessions.add(session);
			listeners.add(listener);
			return () -> detached++;
		}

		private InventoryPacketListener listener() {
			return listeners.getLast();
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler) {
			handlers.put(channelOperation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(ChannelOperation<Q, ?> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A player whose native generations the test replaces: the binding's listener factory runs for each new session
	 * after the previous generation's listener was closed.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final List<InventorySnapshot> snapshots = new ArrayList<>();
		private @Nullable Object session;
		private @Nullable Function<Object, Subscription> listener;
		private @Nullable Subscription generation;

		private RecordingPlayer(@Nullable Object session) {
			this.session = session;
		}

		private void reconnect(Object next) {
			generation.close();
			session = next;
			generation = listener.apply(next);
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
		}

		@Override
		public void disconnect() {
		}

		@Override
		public void rejoin() {
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
			generation = listener.apply(session);
			return () -> generation.close();
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Inventory does not read the view");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Inventory does not change the view");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			assertSame(InventoryOperations.CHANGED, eventDescriptor);
			snapshots.add((InventorySnapshot) payload);
		}
	}
}
