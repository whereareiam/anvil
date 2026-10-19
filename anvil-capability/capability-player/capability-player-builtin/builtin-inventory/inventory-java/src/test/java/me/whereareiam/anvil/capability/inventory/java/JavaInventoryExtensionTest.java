package me.whereareiam.anvil.capability.inventory.java;

import me.whereareiam.anvil.capability.inventory.channel.InventoryChannel;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
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
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetHeldSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.java.api.type.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaInventoryExtensionTest {
	private static final InventoryItem DIAMONDS = item(4, 3);
	private static final Dimension OVERWORLD = new Dimension("minecraft:overworld", -64, 384);

	private final JavaInventoryExtension extension = new JavaInventoryExtension();

	@Test
	void isDiscoveredAsTheEditionWideInventoryExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaInventoryExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.inventory", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void selectsTheHotbarSlot() {
		Fixture fixture = new Fixture();

		assertNull(fixture.operations.execute(InventoryChannel.SELECT, new SlotSelection(3)));

		assertEquals(List.of("inventory.select", "inventory.click"), List.copyOf(fixture.operations.handlers.keySet()));
		assertEquals(List.of(new ServerboundSetCarriedItem(3)), fixture.protocol.sent);
	}

	@Test
	void tellsNothingForTheSlotThePlayerHoldsAlready() {
		Fixture fixture = new Fixture();

		fixture.operations.execute(InventoryChannel.SELECT, new SlotSelection(0));
		fixture.operations.execute(InventoryChannel.SELECT, new SlotSelection(2));
		fixture.operations.execute(InventoryChannel.SELECT, new SlotSelection(2));

		assertEquals(List.of(new ServerboundSetCarriedItem(2)), fixture.protocol.sent);
	}

	@Test
	void holdsTheHotbarSlotTheServerSelectsAndTellsIt() {
		Fixture fixture = new Fixture();

		fixture.protocol.deliver(new ClientboundSetHeldSlot(0));
		fixture.protocol.deliver(new ClientboundSetHeldSlot(5));
		fixture.protocol.deliver(new ClientboundSetHeldSlot(5));
		fixture.protocol.deliver(new ClientboundSetHeldSlot(9));
		fixture.protocol.deliver(new ClientboundSetHeldSlot(-1));

		assertEquals(List.of(new ServerboundSetCarriedItem(5)), fixture.protocol.sent);
	}

	@Test
	void startsEachLoginWithTheFirstSlotToldAndEachRespawnWithItSelected() {
		Fixture fixture = new Fixture();
		fixture.operations.execute(InventoryChannel.SELECT, new SlotSelection(4));

		fixture.protocol.deliver(new ClientboundRespawn(OVERWORLD));
		fixture.protocol.deliver(new ClientboundSetHeldSlot(4));
		fixture.protocol.deliver(new ClientboundLogin(1, OVERWORLD));
		fixture.protocol.deliver(new ClientboundSetHeldSlot(0));
		fixture.protocol.deliver(new ClientboundSetHeldSlot(4));

		assertEquals(List.of(new ServerboundSetCarriedItem(4), new ServerboundSetCarriedItem(4)), fixture.protocol.sent);
	}

	@Test
	void clicksTheObservedContainerWithItsState() {
		Fixture fixture = new Fixture();
		fixture.protocol.deliver(content(1, 7, stack(3)));

		fixture.operations.execute(InventoryChannel.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0));
		fixture.operations.execute(InventoryChannel.CLICK, new InventoryAction(5, InventoryClick.RIGHT, 0));
		fixture.operations.execute(InventoryChannel.CLICK, new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 8));

		assertEquals(List.of(
				click(1, 7, 4, ClickType.PICKUP, 0),
				click(1, 7, 5, ClickType.PICKUP, 1),
				click(1, 7, 4, ClickType.SWAP, 8)
		), fixture.protocol.sent);
	}

	@Test
	void sendsEachClickModeAsItsClickTypeAndButton() {
		Fixture fixture = new Fixture();
		Map<InventoryClick, List<Object>> expected = Map.of(
				InventoryClick.LEFT, List.of(ClickType.PICKUP, 0),
				InventoryClick.RIGHT, List.of(ClickType.PICKUP, 1),
				InventoryClick.SHIFT_LEFT, List.of(ClickType.QUICK_MOVE, 0),
				InventoryClick.HOTBAR_SWAP, List.of(ClickType.SWAP, 8),
				InventoryClick.DROP_ONE, List.of(ClickType.THROW, 0),
				InventoryClick.DROP_STACK, List.of(ClickType.THROW, 1)
		);

		for (InventoryClick click : InventoryClick.values()) {
			fixture.operations.execute(InventoryChannel.CLICK,
					new InventoryAction(4, click, click == InventoryClick.HOTBAR_SWAP ? 8 : 0));
			ServerboundContainerClick packet = (ServerboundContainerClick) fixture.protocol.sent.getLast();
			assertEquals(expected.get(click), List.<Object>of(packet.getClickType(), packet.getButton()), click.name());
		}
	}

	@Test
	void refusesAHotbarButtonOutsideTheHotbarBeforeSending() {
		Fixture fixture = new Fixture();

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> fixture.click(InventoryClick.HOTBAR_SWAP, 9));

		assertEquals("Invalid hotbar button: 9", failure.getMessage());
		assertTrue(fixture.protocol.sent.isEmpty());
	}

	@Test
	void sendsTheOffHandSwapButtonAndRefusesTheButtonAfterIt() {
		Fixture fixture = new Fixture();

		fixture.operations.execute(InventoryChannel.CLICK, new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 40));
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> fixture.click(InventoryClick.HOTBAR_SWAP, 41));

		assertEquals("Invalid hotbar button: 41", failure.getMessage());
		assertEquals(List.of(click(0, 0, 4, ClickType.SWAP, 40)), fixture.protocol.sent);
	}

	@Test
	void passesOnTheRefusalOfAPlayerThatIsNotConnected() {
		Fixture fixture = new Fixture();
		fixture.protocol.connected = false;

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> fixture.operations.execute(InventoryChannel.SELECT, new SlotSelection(3)));
		assertThrows(IllegalStateException.class,
				() -> fixture.click(InventoryClick.LEFT, 0));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertTrue(fixture.protocol.sent.isEmpty());
	}

	@Test
	void publishesEveryChangeOfTheObservedContainer() {
		Fixture fixture = new Fixture();
		InventoryItem stone = item(0, 64);

		fixture.protocol.deliver(new ClientboundOpenScreen(2, "minecraft:generic_9x1", "Chest"));
		fixture.protocol.deliver(content(2, 3, stack(64), null, null, null, stack(3)));
		fixture.protocol.deliver(new ClientboundContainerSetSlot(2, 4, 0, null));
		fixture.protocol.deliver(new ClientboundContainerSetSlot(2, 5, 1, stack(1)));

		assertEquals(List.of(
				snapshot(2, 0),
				snapshot(2, 3, stone, DIAMONDS),
				snapshot(2, 4, DIAMONDS),
				snapshot(2, 5, item(1, 1), DIAMONDS)
		), fixture.player.snapshots);
	}

	@Test
	void ignoresSlotsOfOtherContainersTheCursorAndThePlayersInventoryWhileAContainerIsOpen() {
		Fixture fixture = new Fixture();
		fixture.protocol.deliver(content(2, 3, null, null, null, null, stack(3)));

		fixture.protocol.deliver(new ClientboundContainerSetSlot(0, 9, 36, stack(1)));
		fixture.protocol.deliver(new ClientboundContainerSetSlot(-1, 9, -1, stack(1)));
		fixture.protocol.deliver(new ClientboundSetPlayerInventory(0, stack(1)));

		assertEquals(List.of(snapshot(2, 3, DIAMONDS)), fixture.player.snapshots);
		fixture.operations.execute(InventoryChannel.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0));
		assertEquals(click(2, 3, 4, ClickType.PICKUP, 0), fixture.protocol.sent.getFirst());
	}

	@Test
	void placesTheSlotsOfThePlayersInventoryInThePlayersInventoryContainer() {
		Fixture fixture = new Fixture();
		fixture.protocol.deliver(content(0, 1));

		fixture.protocol.deliver(new ClientboundSetPlayerInventory(0, stack(1)));
		fixture.protocol.deliver(new ClientboundSetPlayerInventory(9, stack(2)));
		fixture.protocol.deliver(new ClientboundSetPlayerInventory(39, stack(3)));
		fixture.protocol.deliver(new ClientboundSetPlayerInventory(40, stack(4)));
		fixture.protocol.deliver(new ClientboundSetPlayerInventory(41, stack(5)));
		fixture.protocol.deliver(new ClientboundSetPlayerInventory(0, null));

		assertEquals(playerInventorySnapshots(), fixture.player.snapshots);
	}

	@Test
	void stopsListeningWhenClosed() {
		Fixture fixture = new Fixture();

		fixture.binding.close();
		fixture.protocol.deliver(new ClientboundOpenScreen(5, "minecraft:generic_9x1", "Chest"));

		assertEquals(Map.of(), fixture.protocol.listeners);
		assertEquals(List.of(), fixture.player.snapshots);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer player = new RecordingPlayer(null);
		RecordingOperations operations = new RecordingOperations();

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, operations));
		assertEquals(Map.of(), operations.handlers);
	}

	private static List<InventorySnapshot> playerInventorySnapshots() {
		return List.of(
				snapshot(0, 1),
				snapshot(0, 1, item(36, 1)),
				snapshot(0, 1, item(9, 2), item(36, 1)),
				snapshot(0, 1, item(5, 3), item(9, 2), item(36, 1)),
				snapshot(0, 1, item(5, 3), item(9, 2), item(36, 1), item(45, 4)),
				snapshot(0, 1, item(5, 3), item(9, 2), item(45, 4))
		);
	}

	private static ClientboundContainerSetContent content(int containerId, int stateId, @Nullable ItemStack... items) {
		return new ClientboundContainerSetContent(containerId, stateId, Arrays.asList(items), null);
	}

	private static ItemStack stack(int count) {
		return new ItemStack(100 + count, count);
	}

	private static InventoryItem item(int slot, int amount) {
		return InventoryItem.builder().slot(slot).protocolId(100 + amount).amount(amount).build();
	}

	private static InventorySnapshot snapshot(int containerId, int stateId, InventoryItem... items) {
		return InventorySnapshot.builder().containerId(containerId).stateId(stateId).items(List.of(items)).build();
	}

	private static ServerboundContainerClick click(int containerId, int stateId, int slot, ClickType type, int button) {
		return ServerboundContainerClick.builder()
				.containerId(containerId)
				.stateId(stateId)
				.slot(slot)
				.button(button)
				.clickType(type)
				.build();
	}

	/**
	 * Binds inventory for one player.
	 */
	private final class Fixture {
		private final RecordingProtocol protocol;
		private final RecordingPlayer player;
		private final RecordingOperations operations = new RecordingOperations();
		private final WorkerBinding binding;

		private Fixture() {
			protocol = new RecordingProtocol();
			player = new RecordingPlayer(protocol);
			binding = extension.bind(player, operations);
		}

		private void click(InventoryClick click, int button) {
			operations.execute(InventoryChannel.CLICK, new InventoryAction(4, click, button));
		}
	}

	/**
	 * Records sent packets and hands delivered packets to the listener of their class.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();
		private final List<ServerboundPacket> sent = new ArrayList<>();
		private boolean connected = true;

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
			if (!connected) throw new IllegalStateException("Player Alice is not connected");

			sent.add(packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The inventory binding observes no sent packets");
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
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(OperationDescriptor<Q, ?> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A player that speaks the given protocol, or none, and records the snapshots it emits.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<InventorySnapshot> snapshots = new ArrayList<>();

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
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
			throw new AssertionError("Inventory does not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Inventory does not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Inventory does not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("Inventory uses the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Inventory uses the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			throw new AssertionError("Inventory keeps its container across connections");
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
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			assertEquals(InventoryChannel.CHANGED, event);
			snapshots.add((InventorySnapshot) payload);
		}
	}
}
