package me.whereareiam.anvil.capability.inventory.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.inventory.channel.InventoryChannel;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.model.ItemDisplay;
import me.whereareiam.anvil.capability.inventory.model.ItemType;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.inventory.type.ItemNameSource;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.PlayerInventoryLayout;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerClose;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.player.ClientboundSetHeldSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.player.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.player.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.java.api.type.interaction.ClickType;
import me.whereareiam.anvil.protocol.java.item.ItemRegistry;
import me.whereareiam.anvil.protocol.java.item.JavaItemData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Installs the built-in inventory capability for players that speak the Java Edition protocol: it registers hotbar
 * selection and container clicks, keeps the screen the player's packets describe and emits every change of it as an
 * {@link InventorySnapshot}. The observed screen and its state ID belong to the player and survive reconnects.
 *
 * <p>The player holds a hotbar slot as the vanilla client does: a test's selection and the server's selection of a
 * hotbar slot select it, and the player tells the server the selected slot whenever it differs from the one the server
 * was last told ({@code MultiPlayerGameMode.ensureHasSentCarriedItem}). A login starts with the first slot selected
 * and told; a respawn selects the first slot of the new player's inventory again, and the server's selection that
 * follows every respawn decides what the player tells. The vanilla client tells the slot with its next tick, the
 * player at once.</p>
 *
 * <p>The player keeps its screens as the vanilla client does ({@code ClientPacketListener}). A container the server
 * opens is the screen until the server closes it, whatever the ID of the closing packet, or opens another; the
 * contents of other containers and the cursor's slot are ignored. The player's own inventory is kept beside it: an
 * opened container shows the main inventory and the hotbar as its last slots, so a change of either shows in both. A
 * new screen is published once the server filled it, and each item carries its type's registry key from the item data
 * of the player's protocol, with its names and lore.</p>
 */
public final class JavaInventoryExtension implements WorkerExtension<Object> {

	@Override
	public @NotNull String id() {
		return InventoryChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		ObservedScreen screen = new ObservedScreen(new Items(JavaItemData.shipped().find(protocol.protocolNumber()).orElse(null)));
		HeldSlot held = new HeldSlot(protocol);
		operations.register(InventoryChannel.SELECT, selection -> {
			held.select(selection.getSlot());
			return null;
		});
		operations.register(InventoryChannel.CLICK, action -> {
			protocol.send(click(screen, action));
			return null;
		});

		Subscription listening = Subscription.all(
				protocol.listen(ClientboundLogin.class, ignored -> held.joined()),
				protocol.listen(ClientboundRespawn.class, ignored -> held.respawned()),
				protocol.listen(ClientboundSetHeldSlot.class, selection -> held.selectedByServer(selection.getSlot())),
				protocol.listen(ClientboundOpenScreen.class, open ->
						screen.open(open.getContainerId(), open.getMenuType(), open.getTitle())),
				protocol.listen(ClientboundContainerClose.class, ignored -> publish(player, screen.close())),
				protocol.listen(ClientboundContainerSetContent.class, content -> publish(player,
						screen.fill(content.getContainerId(), content.getStateId(), content.getItems()))),
				protocol.listen(ClientboundContainerSetSlot.class, slot -> publish(player,
						screen.set(slot.getContainerId(), slot.getStateId(), slot.getSlot(), slot.getItem()))),
				protocol.listen(ClientboundSetPlayerInventory.class, slot ->
						publish(player, screen.setInventory(slot.getSlot(), slot.getItem())))
		);

		return listening::close;
	}

	private static ServerboundContainerClick click(ObservedScreen screen, InventoryAction action) {
		if (action.getClick() == InventoryClick.HOTBAR_SWAP && !swapButton(action.getButton()))
			throw new IllegalArgumentException("Invalid hotbar button: " + action.getButton());

		int slot = action.getSlot();
		return switch (action.getClick()) {
			case LEFT -> screen.click(slot, ClickType.PICKUP, ClickType.PRIMARY_BUTTON);
			case RIGHT -> screen.click(slot, ClickType.PICKUP, ClickType.SECONDARY_BUTTON);
			case SHIFT_LEFT -> screen.click(slot, ClickType.QUICK_MOVE, ClickType.PRIMARY_BUTTON);
			case HOTBAR_SWAP -> screen.click(slot, ClickType.SWAP, action.getButton());
			case DROP_ONE -> screen.click(slot, ClickType.THROW, ClickType.PRIMARY_BUTTON);
			case DROP_STACK -> screen.click(slot, ClickType.THROW, ClickType.SECONDARY_BUTTON);
		};
	}

	/**
	 * Tells whether a hotbar swap's button names a slot to swap with: a hotbar slot or the off hand, by inventory
	 * index.
	 */
	private static boolean swapButton(int button) {
		return PlayerInventoryLayout.hotbar(button) || button == PlayerInventoryLayout.OFF_HAND;
	}

	private static void publish(PlayerBindingContext<Object> player, @Nullable InventorySnapshot snapshot) {
		if (snapshot != null) player.emit(InventoryChannel.CHANGED, snapshot);
	}

	/**
	 * Turns the protocol's stacks into the items of snapshots, keyed by the registry of the player's protocol when
	 * Anvil ships one.
	 */
	@RequiredArgsConstructor
	private static final class Items {
		private final @Nullable ItemRegistry registry;

		private @Nullable InventoryItem item(int slot, @Nullable ItemStack stack) {
			if (stack == null) return null;

			return InventoryItem.builder()
					.slot(slot)
					.type(new ItemType(registry == null ? null : registry.key(stack.getId()).orElse(null), stack.getId()))
					.amount(stack.getCount())
					.display(display(stack))
					.build();
		}

		/**
		 * Names the item as the client does: by its custom name, else by its item name.
		 */
		private static ItemDisplay display(ItemStack stack) {
			ItemDisplay.ItemDisplayBuilder display = ItemDisplay.builder().lore(stack.getLore());
			if (stack.getCustomName() != null) return display.name(stack.getCustomName()).nameSource(ItemNameSource.CUSTOM).build();
			if (stack.getItemName() != null) return display.name(stack.getItemName()).nameSource(ItemNameSource.ITEM).build();

			return display.build();
		}
	}

	/**
	 * The hotbar slot a player selected and the one it last told the server. Packet listeners and operations run on
	 * different threads, so every access holds the monitor.
	 */
	@RequiredArgsConstructor
	private static final class HeldSlot {
		private final JavaProtocol protocol;
		private int selected;
		private int told;

		private synchronized void select(int slot) {
			selected = slot;
			tell();
		}

		private synchronized void selectedByServer(int slot) {
			if (PlayerInventoryLayout.hotbar(slot)) select(slot);
		}

		private synchronized void joined() {
			selected = PlayerInventoryLayout.HOTBAR;
			told = PlayerInventoryLayout.HOTBAR;
		}

		private synchronized void respawned() {
			selected = PlayerInventoryLayout.HOTBAR;
		}

		private void tell() {
			if (selected == told) return;

			protocol.send(new ServerboundSetCarriedItem(selected));
			told = selected;
		}
	}

	/**
	 * The screen a player sees, the player's own inventory beside it and the IDs its clicks carry. Packet listeners
	 * and operations run on different threads, so every access holds the monitor; each change returns the snapshot to
	 * publish, or {@code null} when the packet did not change a filled screen.
	 *
	 * <p>The inventory is kept by inventory index. While the player's inventory is the screen, its items are the
	 * inventory's at their container slots; while another container is, the container keeps its own slots and shows
	 * the inventory's main part and hotbar at its last ones.</p>
	 */
	@RequiredArgsConstructor
	private static final class ObservedScreen {
		private final Items stacks;
		private final Map<Integer, ItemStack> inventory = new TreeMap<>();
		private final Map<Integer, ItemStack> container = new TreeMap<>();
		private int inventoryState;
		private boolean inventoryFilled;

		private int screen;
		private int containerId = PlayerInventoryLayout.CONTAINER_ID;
		private int containerState;
		private int containerSlots;
		private boolean containerFilled;
		private @Nullable String menuType;
		private @Nullable Text title;

		private synchronized void open(int containerId, @NotNull String menuType, @NotNull Text title) {
			this.screen++;
			this.containerId = containerId;
			this.containerState = 0;
			this.containerSlots = 0;
			this.containerFilled = false;
			this.menuType = menuType;
			this.title = title;
			container.clear();
		}

		private synchronized @Nullable InventorySnapshot close() {
			if (containerId == PlayerInventoryLayout.CONTAINER_ID) return null;

			screen++;
			containerId = PlayerInventoryLayout.CONTAINER_ID;
			menuType = null;
			title = null;
			container.clear();
			return snapshot();
		}

		private synchronized @Nullable InventorySnapshot fill(int containerId, int stateId, List<@Nullable ItemStack> stacks) {
			if (containerId == PlayerInventoryLayout.CONTAINER_ID) {
				inventoryState = stateId;
				inventoryFilled = true;
				inventory.clear();
				for (int slot = 0; slot < stacks.size(); slot++)
					putInventory(PlayerInventoryLayout.inventoryIndex(slot), stacks.get(slot));
				if (this.containerId == PlayerInventoryLayout.CONTAINER_ID) return published(PlayerInventoryLayout.CONTAINER_ID);

				for (int index = PlayerInventoryLayout.HOTBAR; index < PlayerInventoryLayout.ARMOR; index++)
					showInventory(index);
				return published(this.containerId);
			}
			if (containerId != this.containerId) return null;

			containerState = stateId;
			containerSlots = stacks.size();
			containerFilled = true;
			container.clear();
			for (int slot = 0; slot < stacks.size(); slot++)
				putContainer(slot, stacks.get(slot));
			return snapshot();
		}

		private synchronized @Nullable InventorySnapshot set(int containerId, int stateId, int slot, @Nullable ItemStack stack) {
			if (slot < 0) return null;
			if (containerId == PlayerInventoryLayout.CONTAINER_ID) {
				inventoryState = stateId;
				OptionalInt index = PlayerInventoryLayout.inventoryIndex(slot);
				putInventory(index, stack);
				if (this.containerId == PlayerInventoryLayout.CONTAINER_ID) return published(PlayerInventoryLayout.CONTAINER_ID);

				return index.isPresent() && showInventory(index.getAsInt()) ? published(this.containerId) : null;
			}
			if (containerId != this.containerId) return null;

			containerState = stateId;
			putContainer(slot, stack);
			return published(containerId);
		}

		private synchronized @Nullable InventorySnapshot setInventory(int index, @Nullable ItemStack stack) {
			if (PlayerInventoryLayout.containerSlot(index).isEmpty()) return null;

			putInventory(OptionalInt.of(index), stack);
			if (containerId == PlayerInventoryLayout.CONTAINER_ID) return published(PlayerInventoryLayout.CONTAINER_ID);

			return showInventory(index) ? published(containerId) : null;
		}

		private synchronized @NotNull ServerboundContainerClick click(int slot, ClickType clickType, int button) {
			boolean ownInventory = containerId == PlayerInventoryLayout.CONTAINER_ID;
			return ServerboundContainerClick.builder()
					.containerId(containerId)
					.stateId(ownInventory ? inventoryState : containerState)
					.slot(slot)
					.button(button)
					.clickType(clickType)
					.build();
		}

		/**
		 * Puts a stack of an opened container, mirroring a slot that shows the inventory into it.
		 */
		private void putContainer(int slot, @Nullable ItemStack stack) {
			put(container, slot, stack);
			putInventory(PlayerInventoryLayout.menuInventoryIndex(slot, containerSlots), stack);
		}

		/**
		 * Shows an inventory slot in the opened container, when the container shows it.
		 *
		 * @return whether the container shows the slot
		 */
		private boolean showInventory(int index) {
			OptionalInt slot = PlayerInventoryLayout.menuSlot(index, containerSlots);
			if (slot.isEmpty()) return false;

			put(container, slot.getAsInt(), inventory.get(index));
			return true;
		}

		private void putInventory(OptionalInt index, @Nullable ItemStack stack) {
			if (index.isPresent()) put(inventory, index.getAsInt(), stack);
		}

		private static void put(Map<Integer, ItemStack> slots, int slot, @Nullable ItemStack stack) {
			if (stack == null) {
				slots.remove(slot);
				return;
			}

			slots.put(slot, stack);
		}

		/**
		 * Returns the snapshot of the screen when the changed container is the screen and the server filled it.
		 */
		private @Nullable InventorySnapshot published(int changed) {
			if (changed != containerId) return null;

			boolean ownInventory = containerId == PlayerInventoryLayout.CONTAINER_ID;
			return (ownInventory ? inventoryFilled : containerFilled) ? snapshot() : null;
		}

		private InventorySnapshot snapshot() {
			boolean ownInventory = containerId == PlayerInventoryLayout.CONTAINER_ID;
			InventorySnapshot.InventorySnapshotBuilder snapshot = InventorySnapshot.builder()
					.containerId(containerId)
					.stateId(ownInventory ? inventoryState : containerState)
					.screen(screen)
					.menuType(menuType)
					.title(title);
			if (!ownInventory) {
				container.forEach((slot, stack) -> snapshot.item(stacks.item(slot, stack)));
				return snapshot.build();
			}

			Map<Integer, ItemStack> bySlot = new TreeMap<>();
			inventory.forEach((index, stack) -> bySlot.put(PlayerInventoryLayout.containerSlot(index).orElseThrow(), stack));
			bySlot.forEach((slot, stack) -> snapshot.item(stacks.item(slot, stack)));
			return snapshot.build();
		}
	}
}
