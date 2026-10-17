package me.whereareiam.anvil.capability.inventory.java;

import me.whereareiam.anvil.capability.inventory.channel.InventoryChannel;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.java.api.type.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Installs the built-in inventory capability for players that speak the Java Edition protocol: it registers hotbar
 * selection and container clicks, keeps the container the player's packets describe and emits every change of it as
 * an {@link InventorySnapshot}. The observed container and its state ID belong to the player and survive reconnects.
 *
 * <p>The observed container is the one the server last opened or filled. Slot updates of other containers and of
 * the cursor are ignored, and changes addressed to the player's own inventory are applied only while the player's
 * inventory container is the observed one. Releases before Minecraft 1.21.2 address the player's own inventory
 * through the slot packet of container {@code -2}, later ones with a packet of its own.</p>
 */
public final class JavaInventoryExtension implements WorkerExtension<Object> {
	private static final int INVENTORY_INDEX_CONTAINER = -2;
	private static final int OFF_HAND_BUTTON = 40;

	@Override
	public @NotNull String id() {
		return InventoryChannel.ID;
	}

	@Override
	public @NotNull Optional<String> libraryId() {
		return Optional.empty();
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		ObservedContainer container = new ObservedContainer();
		operations.register(InventoryChannel.SELECT, selection -> {
			protocol.send(new ServerboundSetCarriedItem(selection.getSlot()));
			return null;
		});
		operations.register(InventoryChannel.CLICK, action -> {
			protocol.send(click(container, action));
			return null;
		});

		boolean playerInventoryPacket = protocol.supports(ClientboundSetPlayerInventory.class);
		List<Subscription> subscriptions = new ArrayList<>();
		subscriptions.add(protocol.listen(ClientboundOpenScreen.class, open ->
				publish(player, container.open(open.getContainerId()))));
		subscriptions.add(protocol.listen(ClientboundContainerSetContent.class, content -> publish(player,
				container.replace(content.getContainerId(), content.getStateId(), items(content.getItems())))));
		subscriptions.add(protocol.listen(ClientboundContainerSetSlot.class, slot ->
				publish(player, slot(container, slot, playerInventoryPacket))));
		if (playerInventoryPacket)
			subscriptions.add(protocol.listen(ClientboundSetPlayerInventory.class, slot ->
					publish(player, container.setPlayerSlot(slot.getSlot(), slot.getItem()))));

		return () -> subscriptions.forEach(Subscription::close);
	}

	private static ServerboundContainerClick click(ObservedContainer container, InventoryAction action) {
		if (action.getClick() == InventoryClick.HOTBAR_SWAP && !swapButton(action.getButton()))
			throw new IllegalArgumentException("Invalid hotbar button: " + action.getButton());

		int slot = action.getSlot();
		return switch (action.getClick()) {
			case LEFT -> container.click(slot, ClickType.PICKUP, 0);
			case RIGHT -> container.click(slot, ClickType.PICKUP, 1);
			case SHIFT_LEFT -> container.click(slot, ClickType.QUICK_MOVE, 0);
			case HOTBAR_SWAP -> container.click(slot, ClickType.SWAP, action.getButton());
			case DROP_ONE -> container.click(slot, ClickType.THROW, 0);
			case DROP_STACK -> container.click(slot, ClickType.THROW, 1);
		};
	}

	private static boolean swapButton(int button) {
		return button >= 0 && button <= 8 || button == OFF_HAND_BUTTON;
	}

	private static @Nullable InventorySnapshot slot(
			ObservedContainer container,
			ClientboundContainerSetSlot slot,
			boolean playerInventoryPacket
	) {
		if (!playerInventoryPacket && slot.getContainerId() == INVENTORY_INDEX_CONTAINER)
			return container.setPlayerSlot(slot.getSlot(), slot.getItem());

		InventoryItem item = item(slot.getSlot(), slot.getItem());
		return container.set(slot.getContainerId(), slot.getStateId(), slot.getSlot(), item);
	}

	private static void publish(PlayerBindingContext<Object> player, @Nullable InventorySnapshot snapshot) {
		if (snapshot != null) player.emit(InventoryChannel.CHANGED, snapshot);
	}

	private static List<InventoryItem> items(List<@Nullable ItemStack> stacks) {
		List<InventoryItem> items = new ArrayList<>();
		for (int slot = 0; slot < stacks.size(); slot++) {
			InventoryItem item = item(slot, stacks.get(slot));
			if (item != null) items.add(item);
		}

		return items;
	}

	private static @Nullable InventoryItem item(int slot, @Nullable ItemStack stack) {
		if (stack == null) return null;

		return InventoryItem.builder()
				.slot(slot)
				.protocolId(stack.getId())
				.amount(stack.getCount())
				.build();
	}

	/**
	 * The container a player observes and the IDs its clicks carry. Packet listeners and operations run on different
	 * threads, so every access holds the monitor; each change returns the snapshot to publish, or {@code null} when the
	 * packet did not change the observed container.
	 */
	private static final class ObservedContainer {
		private static final int PLAYER_INVENTORY = 0;

		private final Map<Integer, InventoryItem> items = new TreeMap<>();
		private int containerId;
		private int stateId;

		private synchronized @NotNull InventorySnapshot open(int containerId) {
			this.containerId = containerId;
			this.stateId = 0;
			items.clear();
			return snapshot();
		}

		private synchronized @NotNull InventorySnapshot replace(
				int containerId,
				int stateId,
				List<InventoryItem> contents
		) {
			this.containerId = containerId;
			this.stateId = stateId;
			items.clear();
			contents.forEach(item -> items.put(item.getSlot(), item));
			return snapshot();
		}

		private synchronized @Nullable InventorySnapshot set(
				int containerId,
				int stateId,
				int slot,
				@Nullable InventoryItem item
		) {
			if (containerId != this.containerId || slot < 0) return null;

			this.stateId = stateId;
			put(slot, item);
			return snapshot();
		}

		private synchronized @Nullable InventorySnapshot setPlayerSlot(int index, @Nullable ItemStack stack) {
			int slot = playerInventorySlot(index);
			if (containerId != PLAYER_INVENTORY || slot < 0) return null;

			put(slot, item(slot, stack));
			return snapshot();
		}

		private synchronized @NotNull ServerboundContainerClick click(int slot, ClickType clickType, int button) {
			return ServerboundContainerClick.builder()
					.containerId(containerId)
					.stateId(stateId)
					.slot(slot)
					.button(button)
					.clickType(clickType)
					.build();
		}

		private void put(int slot, @Nullable InventoryItem item) {
			if (item == null) {
				items.remove(slot);
				return;
			}

			items.put(slot, item);
		}

		private InventorySnapshot snapshot() {
			return InventorySnapshot.builder()
					.containerId(containerId)
					.stateId(stateId)
					.items(items.values())
					.build();
		}

		/**
		 * Maps an index of the player's inventory to its slot in the player's inventory container, which lists the
		 * crafting slots, the armor from head to feet, the main inventory, the hotbar and the off hand.
		 *
		 * @return the container slot, or {@code -1} for an index the container does not show
		 */
		private static int playerInventorySlot(int index) {
			if (index < 0) return -1;
			if (index <= 8) return 36 + index;
			if (index <= 35) return index;
			if (index <= 39) return 44 - index;
			if (index == 40) return 45;

			return -1;
		}
	}
}
