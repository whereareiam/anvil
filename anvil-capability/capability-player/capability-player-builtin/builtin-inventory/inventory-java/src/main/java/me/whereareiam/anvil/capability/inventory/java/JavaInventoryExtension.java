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
import me.whereareiam.anvil.protocol.java.api.PlayerInventoryLayout;
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
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Installs the built-in inventory capability for players that speak the Java Edition protocol: it registers hotbar
 * selection and container clicks, keeps the container the player's packets describe and emits every change of it as
 * an {@link InventorySnapshot}. The observed container and its state ID belong to the player and survive reconnects.
 *
 * <p>The observed container is the one the server last opened or filled. Slot updates of other containers and of
 * the cursor are ignored, and changes addressed to the player's own inventory are applied only while the player's
 * inventory container is the observed one.</p>
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
		ObservedContainer container = new ObservedContainer();
		operations.register(InventoryChannel.SELECT, selection -> {
			protocol.send(new ServerboundSetCarriedItem(selection.getSlot()));
			return null;
		});
		operations.register(InventoryChannel.CLICK, action -> {
			protocol.send(click(container, action));
			return null;
		});

		Subscription listening = Subscription.all(
				protocol.listen(ClientboundOpenScreen.class, open ->
						publish(player, container.open(open.getContainerId()))),
				protocol.listen(ClientboundContainerSetContent.class, content -> publish(player,
						container.replace(content.getContainerId(), content.getStateId(), items(content.getItems())))),
				protocol.listen(ClientboundContainerSetSlot.class, slot -> publish(player, container.set(
						slot.getContainerId(), slot.getStateId(), slot.getSlot(),
								item(slot.getSlot(), slot.getItem())))),
				protocol.listen(ClientboundSetPlayerInventory.class, slot ->
						publish(player, container.setPlayerSlot(slot.getSlot(), slot.getItem())))
		);

		return listening::close;
	}

	private static ServerboundContainerClick click(ObservedContainer container, InventoryAction action) {
		if (action.getClick() == InventoryClick.HOTBAR_SWAP && !swapButton(action.getButton()))
			throw new IllegalArgumentException("Invalid hotbar button: " + action.getButton());

		int slot = action.getSlot();
		return switch (action.getClick()) {
			case LEFT -> container.click(slot, ClickType.PICKUP, ClickType.PRIMARY_BUTTON);
			case RIGHT -> container.click(slot, ClickType.PICKUP, ClickType.SECONDARY_BUTTON);
			case SHIFT_LEFT -> container.click(slot, ClickType.QUICK_MOVE, ClickType.PRIMARY_BUTTON);
			case HOTBAR_SWAP -> container.click(slot, ClickType.SWAP, action.getButton());
			case DROP_ONE -> container.click(slot, ClickType.THROW, ClickType.PRIMARY_BUTTON);
			case DROP_STACK -> container.click(slot, ClickType.THROW, ClickType.SECONDARY_BUTTON);
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
			OptionalInt slot = PlayerInventoryLayout.containerSlot(index);
			if (containerId != PlayerInventoryLayout.CONTAINER_ID || slot.isEmpty()) return null;

			put(slot.getAsInt(), item(slot.getAsInt(), stack));

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
	}
}
