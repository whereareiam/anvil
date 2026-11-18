package me.whereareiam.anvil.capability.inventory.java;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.inventory.channel.InventoryChannel;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.ItemDisplay;
import me.whereareiam.anvil.capability.inventory.model.ItemType;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.inventory.type.ItemNameSource;
import me.whereareiam.anvil.edition.java.data.item.JavaItemData;
import me.whereareiam.anvil.edition.java.protocol.PlayerInventoryLayout;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerClose;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerSetContent;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerSetSlot;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundOpenScreen;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ServerboundContainerClick;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.player.ClientboundSetHeldSlot;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.player.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.player.ServerboundSetCarriedItem;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.ClickType;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.worker.TestOperations;
import me.whereareiam.anvil.protocol.worker.TestWorkerPlayer;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaInventoryExtensionTest {
	private static final InventoryItem DIAMONDS = item(4, 3);
	private static final DimensionType OVERWORLD = DimensionType.known("minecraft:overworld");
	private static final List<Class<? extends ClientboundPacket>> LISTENED = List.of(ClientboundLogin.class,
			ClientboundRespawn.class, ClientboundSetHeldSlot.class, ClientboundOpenScreen.class,
			ClientboundContainerClose.class, ClientboundContainerSetContent.class, ClientboundContainerSetSlot.class,
			ClientboundSetPlayerInventory.class);
	/**
	 * The slots of a single-row chest: its nine own slots, then the player's main inventory and hotbar.
	 */
	private static final int CHEST_SLOTS = 9 + 36;

	private final JavaInventoryExtension extension = new JavaInventoryExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();

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
		connect();

		assertNull(operations.call(InventoryChannel.SELECT, new SlotSelection(3)));

		assertEquals(List.of("inventory.select", "inventory.click"), operations.registered());
		assertEquals(List.of(new ServerboundSetCarriedItem(3)), player.sent());
	}

	@Test
	void tellsNothingForTheSlotThePlayerHoldsAlready() {
		connect();

		operations.call(InventoryChannel.SELECT, new SlotSelection(0));
		operations.call(InventoryChannel.SELECT, new SlotSelection(2));
		operations.call(InventoryChannel.SELECT, new SlotSelection(2));

		assertEquals(List.of(new ServerboundSetCarriedItem(2)), player.sent());
	}

	@Test
	void holdsTheHotbarSlotTheServerSelectsAndTellsIt() {
		connect();

		player.deliver(new ClientboundSetHeldSlot(0));
		player.deliver(new ClientboundSetHeldSlot(5));
		player.deliver(new ClientboundSetHeldSlot(5));
		player.deliver(new ClientboundSetHeldSlot(9));
		player.deliver(new ClientboundSetHeldSlot(-1));

		assertEquals(List.of(new ServerboundSetCarriedItem(5)), player.sent());
	}

	@Test
	void startsEachLoginWithTheFirstSlotToldAndEachRespawnWithItSelected() {
		connect();
		operations.call(InventoryChannel.SELECT, new SlotSelection(4));

		player.deliver(ClientboundRespawn.builder().dimension("minecraft:overworld").dimensionType(OVERWORLD).build());
		player.deliver(new ClientboundSetHeldSlot(4));
		player.deliver(login(1));
		player.deliver(new ClientboundSetHeldSlot(0));
		player.deliver(new ClientboundSetHeldSlot(4));

		assertEquals(List.of(new ServerboundSetCarriedItem(4), new ServerboundSetCarriedItem(4)), player.sent());
	}

	@Test
	void clicksTheObservedContainerWithItsState() {
		connect();
		player.deliver(new ClientboundOpenScreen(1, "minecraft:generic_9x1", Text.literal("Chest")));
		player.deliver(content(1, 7, stack(3)));

		operations.call(InventoryChannel.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0));
		operations.call(InventoryChannel.CLICK, new InventoryAction(5, InventoryClick.RIGHT, 0));
		operations.call(InventoryChannel.CLICK, new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 8));

		assertEquals(List.of(
				click(1, 7, 4, ClickType.PICKUP, 0),
				click(1, 7, 5, ClickType.PICKUP, 1),
				click(1, 7, 4, ClickType.SWAP, 8)
		), player.sent());
	}

	@Test
	void sendsEachClickModeAsItsClickTypeAndButton() {
		connect();
		Map<InventoryClick, List<Object>> expected = Map.of(
				InventoryClick.LEFT, List.of(ClickType.PICKUP, 0),
				InventoryClick.RIGHT, List.of(ClickType.PICKUP, 1),
				InventoryClick.SHIFT_LEFT, List.of(ClickType.QUICK_MOVE, 0),
				InventoryClick.HOTBAR_SWAP, List.of(ClickType.SWAP, 8),
				InventoryClick.DROP_ONE, List.of(ClickType.THROW, 0),
				InventoryClick.DROP_STACK, List.of(ClickType.THROW, 1)
		);

		for (InventoryClick click : InventoryClick.values()) {
			operations.call(InventoryChannel.CLICK,
					new InventoryAction(4, click, click == InventoryClick.HOTBAR_SWAP ? 8 : 0));
			ServerboundContainerClick packet = (ServerboundContainerClick) player.sent().getLast();
			assertEquals(expected.get(click), List.<Object>of(packet.getClickType(), packet.getButton()), click.name());
		}
	}

	@Test
	void refusesAHotbarButtonOutsideTheHotbarBeforeSending() {
		connect();

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> click(InventoryClick.HOTBAR_SWAP, 9));

		assertEquals("Invalid hotbar button: 9", failure.getMessage());
		assertTrue(player.sent().isEmpty());
	}

	@Test
	void sendsTheOffHandSwapButtonAndRefusesTheButtonAfterIt() {
		connect();

		operations.call(InventoryChannel.CLICK, new InventoryAction(4, InventoryClick.HOTBAR_SWAP, 40));
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> click(InventoryClick.HOTBAR_SWAP, 41));

		assertEquals("Invalid hotbar button: 41", failure.getMessage());
		assertEquals(List.of(click(0, 0, 4, ClickType.SWAP, 40)), player.sent());
	}

	@Test
	void passesOnTheRefusalOfAPlayerThatIsNotConnected() {
		connect();
		player.disconnect();

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.call(InventoryChannel.SELECT, new SlotSelection(3)));
		assertThrows(IllegalStateException.class,
				() -> click(InventoryClick.LEFT, 0));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertTrue(player.sent().isEmpty());
	}

	@Test
	void publishesAnOpenedContainerOnceTheServerFilledItAndEveryChangeOfIt() {
		connect();
		InventoryItem stone = item(0, 64);

		player.deliver(new ClientboundOpenScreen(2, "minecraft:generic_9x1", Text.literal("Chest")));
		assertEquals(List.of(), snapshots());
		player.deliver(content(2, 3, stack(64), null, null, null, stack(3)));
		player.deliver(new ClientboundContainerSetSlot(2, 4, 0, null));
		player.deliver(new ClientboundContainerSetSlot(2, 5, 1, stack(1)));

		assertEquals(List.of(
				chest(1, 3, stone, DIAMONDS),
				chest(1, 4, DIAMONDS),
				chest(1, 5, item(1, 1), DIAMONDS)
		), snapshots());
	}

	@Test
	void ignoresTheContentsOfContainersThatAreNotOpenAndTheCursor() {
		connect();
		player.deliver(new ClientboundOpenScreen(2, "minecraft:generic_9x1", Text.literal("Chest")));
		player.deliver(content(2, 3, null, null, null, null, stack(3)));

		player.deliver(content(5, 9, stack(1)));
		player.deliver(new ClientboundContainerSetSlot(5, 9, 0, stack(1)));
		player.deliver(new ClientboundContainerSetSlot(-1, 9, -1, stack(1)));

		assertEquals(List.of(chest(1, 3, DIAMONDS)), snapshots());
		operations.call(InventoryChannel.CLICK, new InventoryAction(4, InventoryClick.LEFT, 0));
		assertEquals(click(2, 3, 4, ClickType.PICKUP, 0), player.sent().getFirst());
	}

	@Test
	void showsThePlayersMainInventoryAndHotbarBelowAnOpenedContainer() {
		connect();
		player.deliver(content(0, 1));
		player.deliver(new ClientboundSetPlayerInventory(9, stack(2)));
		player.deliver(new ClientboundOpenScreen(2, "minecraft:generic_9x1", Text.literal("Chest")));
		player.deliver(chestContent(3));

		player.deliver(new ClientboundSetPlayerInventory(0, stack(1)));
		player.deliver(new ClientboundContainerSetSlot(0, 2, 36, null));
		player.deliver(new ClientboundSetPlayerInventory(39, stack(4)));

		assertEquals(List.of(
				snapshot(0, 1),
				snapshot(0, 1, item(9, 2)),
				chest(1, 3, item(9, 2)),
				chest(1, 3, item(9, 2), item(36, 1)),
				chest(1, 3, item(9, 2))
		), snapshots());
	}

	@Test
	void returnsToThePlayersInventoryWithWhatChangedBelowTheClosedContainer() {
		connect();
		player.deliver(content(0, 1, null, null, null, null, null, null, null, null, null, stack(2)));
		player.deliver(new ClientboundOpenScreen(2, "minecraft:generic_9x1", Text.literal("Chest")));
		player.deliver(chestContent(3, 9, stack(5)));

		player.deliver(new ClientboundContainerClose(7));
		player.deliver(new ClientboundContainerClose(7));
		player.deliver(new ClientboundContainerSetSlot(2, 4, 0, stack(1)));

		assertEquals(InventorySnapshot.builder().containerId(0).stateId(1).screen(2).item(item(9, 5)).build(),
				snapshots().getLast());
		assertEquals(3, snapshots().size());
		operations.call(InventoryChannel.CLICK, new InventoryAction(9, InventoryClick.LEFT, 0));
		assertEquals(click(0, 1, 9, ClickType.PICKUP, 0), player.sent().getFirst());
	}

	@Test
	void keysEachItemByTheItemDataOfThePlayersProtocolAndNamesItAsTheClientDoes() {
		connect();
		int emerald = JavaItemData.shipped().require(TestJavaPlayer.PROTOCOL).id("minecraft:emerald").orElseThrow();
		ItemStack named = ItemStack.builder()
				.id(emerald)
				.count(1)
				.customName(Text.literal("Buy"))
				.itemName(Text.literal("Token"))
				.lore(Text.literal("Costs 64 coins"))
				.build();

		player.deliver(content(0, 1));
		player.deliver(new ClientboundSetPlayerInventory(9, named));

		assertEquals(InventoryItem.builder()
				.slot(9)
				.type(new ItemType("minecraft:emerald", emerald))
				.amount(1)
				.display(ItemDisplay.builder()
						.name(Text.literal("Buy"))
						.nameSource(ItemNameSource.CUSTOM)
						.lore(Text.literal("Costs 64 coins"))
						.build())
				.build(), snapshots().getLast().getItems().getFirst());
		player.deliver(new ClientboundSetPlayerInventory(9, named.toBuilder().customName(null).build()));
		assertEquals(ItemDisplay.builder()
				.name(Text.literal("Token"))
				.nameSource(ItemNameSource.ITEM)
				.lore(Text.literal("Costs 64 coins"))
				.build(), snapshots().getLast().getItems().getFirst().getDisplay());
	}

	@Test
	void leavesTheTypeOutForAProtocolWithoutItemData() {
		TestJavaPlayer unknown = new TestJavaPlayer("Alice", MinecraftVersion.parse("26.2"), 776, type -> true);
		extension.bind(unknown, operations);
		unknown.connect();

		unknown.deliver(content(0, 1));
		unknown.deliver(new ClientboundSetPlayerInventory(9, stack(1)));

		assertNull(unknown.emitted(InventoryChannel.CHANGED).getLast().getItems().getFirst().getType().getIdentifier());
	}

	@Test
	void placesTheSlotsOfThePlayersInventoryInThePlayersInventoryContainer() {
		connect();
		player.deliver(content(0, 1));

		player.deliver(new ClientboundSetPlayerInventory(0, stack(1)));
		player.deliver(new ClientboundSetPlayerInventory(9, stack(2)));
		player.deliver(new ClientboundSetPlayerInventory(39, stack(3)));
		player.deliver(new ClientboundSetPlayerInventory(40, stack(4)));
		player.deliver(new ClientboundSetPlayerInventory(41, stack(5)));
		player.deliver(new ClientboundSetPlayerInventory(0, null));

		assertEquals(playerInventorySnapshots(), snapshots());
	}

	@Test
	void stopsListeningWhenClosed() {
		WorkerBinding binding = extension.bind(player, operations);
		player.connect();

		binding.close();
		player.deliver(new ClientboundOpenScreen(5, "minecraft:generic_9x1", Text.literal("Chest")));

		for (Class<? extends ClientboundPacket> type : LISTENED)
			assertFalse(player.protocol().wants(type), type.getSimpleName());
		assertEquals(List.of(), snapshots());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		assertEquals(List.of(), operations.registered());
	}

	private void connect() {
		extension.bind(player, operations);
		player.connect();
	}

	private void click(InventoryClick click, int button) {
		operations.call(InventoryChannel.CLICK, new InventoryAction(4, click, button));
	}

	private List<InventorySnapshot> snapshots() {
		return player.emitted(InventoryChannel.CHANGED);
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
		return ItemStack.of(100 + count, count);
	}

	private static InventoryItem item(int slot, int amount) {
		return InventoryItem.builder()
				.slot(slot)
				.type(new ItemType(JavaItemData.shipped().require(TestJavaPlayer.PROTOCOL).key(100 + amount).orElseThrow(),
						100 + amount))
				.amount(amount)
				.build();
	}

	private static InventorySnapshot snapshot(int containerId, int stateId, InventoryItem... items) {
		return InventorySnapshot.builder().containerId(containerId).stateId(stateId).items(List.of(items)).build();
	}

	private static InventorySnapshot chest(int screen, int stateId, InventoryItem... items) {
		return InventorySnapshot.builder()
				.containerId(2)
				.stateId(stateId)
				.screen(screen)
				.menuType("minecraft:generic_9x1")
				.title(Text.literal("Chest"))
				.items(List.of(items))
				.build();
	}

	/**
	 * Fills the single-row chest, showing the player's inventory below it as the server does, with a stack in one of
	 * its slots.
	 */
	private ClientboundContainerSetContent chestContent(int stateId, int slot, ItemStack stack) {
		ItemStack[] items = new ItemStack[CHEST_SLOTS];
		items[slot] = stack;
		return new ClientboundContainerSetContent(2, stateId, Arrays.asList(items), null);
	}

	/**
	 * Fills the single-row chest with what the player's inventory holds below it, as the server does.
	 */
	private ClientboundContainerSetContent chestContent(int stateId) {
		ItemStack[] items = new ItemStack[CHEST_SLOTS];
		for (InventoryItem item : snapshots().getLast().getItems()) {
			int index = PlayerInventoryLayout.inventoryIndex(item.getSlot()).orElseThrow();
			PlayerInventoryLayout.menuSlot(index, CHEST_SLOTS).ifPresent(slot ->
					items[slot] = ItemStack.of(item.getType().getProtocolId(), item.getAmount()));
		}
		return new ClientboundContainerSetContent(2, stateId, Arrays.asList(items), null);
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

	private static ClientboundLogin login(int entityId) {
		return ClientboundLogin.builder()
				.entityId(new EntityId(entityId))
				.level("minecraft:overworld")
				.dimension("minecraft:overworld")
				.dimensionType(OVERWORLD)
				.build();
	}
}
