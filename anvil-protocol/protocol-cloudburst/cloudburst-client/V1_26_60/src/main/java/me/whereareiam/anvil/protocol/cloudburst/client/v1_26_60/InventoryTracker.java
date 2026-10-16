package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DestroyAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TransferItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;

/**
 * The item stacks and hotbar selection one connection's client believes its player has, kept as a Bedrock client
 * keeps them. It follows the server's contents and slot updates of the player's inventory, off hand, armor and user
 * interface containers for the whole connection, and of each container the server opens until it is closed; the
 * server's hotbar selection and mob equipment of the player's own entity; and the item stack responses that confirm
 * or reject the client's requests.
 *
 * <p>Packets the client sends are its own prediction: a mob equipment packet selects its slot, an inventory
 * transaction sets each container slot it names to the item the slot changes to, and an item stack request moves
 * stacks as its take, place, swap, drop and destroy actions say, naming each stack it changed by the request ID until
 * the server's response names it. A rejected request restores the slots it changed; the server corrects a rejected
 * transaction by sending the contents again.</p>
 *
 * <p>Packets arrive on the network thread while operations send from others, so every access holds the monitor.</p>
 */
final class InventoryTracker {
	private static final int HOTBAR_SIZE = 9;

	private final Map<Integer, Map<Integer, ItemData>> containers = new HashMap<>();
	private final SequencedSet<Integer> opened = new LinkedHashSet<>();
	private final Map<Integer, Map<Slot, ItemData>> predictions = new HashMap<>();
	private long runtimeEntityId;
	private @Nullable Boolean serverInventory;
	private int selected;
	private int requestId = 1;

	/**
	 * Applies a packet the server sent.
	 *
	 * @param packet received packet
	 */
	synchronized void received(@NotNull BedrockPacket packet) {
		switch (packet) {
			case StartGamePacket start -> {
				runtimeEntityId = start.getRuntimeEntityId();
				serverInventory = start.isInventoriesServerAuthoritative();
			}
			case ContainerOpenPacket open -> {
				if (serverContainer(open.getId())) opened.add((int) open.getId());
			}
			case ContainerClosePacket close -> close(close.getId());
			case InventoryContentPacket content -> replace(content.getContainerId(), content.getContents());
			case InventorySlotPacket slot -> {
				if (tracked(slot.getContainerId())) put(new Slot(slot.getContainerId(), slot.getSlot()), slot.getItem());
			}
			case PlayerHotbarPacket hotbar -> {
				if (hotbar.isSelectHotbarSlot() && hotbar.getContainerId() == ContainerId.INVENTORY) select(hotbar.getSelectedHotbarSlot());
			}
			case MobEquipmentPacket equipment -> {
				if (equipment.getRuntimeEntityId() == runtimeEntityId && equipment.getContainerId() == ContainerId.INVENTORY)
					select(equipment.getHotbarSlot());
			}
			case ItemStackResponsePacket response -> response.getEntries().forEach(this::respond);
			default -> { }
		}
	}

	/**
	 * Applies a packet the client is about to send as its own prediction.
	 *
	 * @param packet packet to send
	 */
	synchronized void sent(@NotNull BedrockPacket packet) {
		switch (packet) {
			case MobEquipmentPacket equipment -> {
				if (equipment.getContainerId() == ContainerId.INVENTORY) select(equipment.getHotbarSlot());
			}
			case InventoryTransactionPacket transaction -> {
				if (transaction.getTransactionType() == InventoryTransactionType.NORMAL) transaction.getActions().forEach(this::apply);
			}
			case ItemStackRequestPacket request -> request.getRequests().forEach(this::predict);
			case ContainerClosePacket close -> close(close.getId());
			default -> { }
		}
	}

	/**
	 * Tells whether the game start made the server authoritative over the player's inventory.
	 *
	 * @throws IllegalStateException before the game start arrived
	 */
	synchronized boolean serverAuthoritative() {
		if (serverInventory == null) throw new IllegalStateException("The Bedrock session has not started its game yet");

		return serverInventory;
	}

	/**
	 * Takes the next item stack request ID: {@code -1}, then two less each time.
	 */
	synchronized int nextRequestId() {
		requestId -= 2;
		return requestId;
	}

	synchronized int selectedSlot() {
		return selected;
	}

	synchronized @NotNull Optional<ItemData> item(int containerId, int slot) {
		Map<Integer, ItemData> container = containers.get(containerId);
		return container == null ? Optional.empty() : Optional.ofNullable(container.get(slot));
	}

	/**
	 * Builds the mob equipment packet with which the client selects a hotbar slot: its own entity holding the
	 * slot's item from the inventory container.
	 *
	 * @param slot hotbar slot
	 * @throws IllegalArgumentException when the slot is outside the hotbar
	 * @throws IllegalStateException before the game start arrived
	 */
	synchronized @NotNull MobEquipmentPacket equip(int slot) {
		if (slot < 0 || slot >= HOTBAR_SIZE) throw new IllegalArgumentException("Invalid hotbar slot: " + slot);
		if (serverInventory == null) throw new IllegalStateException("The Bedrock session has not started its game yet");

		MobEquipmentPacket packet = new MobEquipmentPacket();
		packet.setRuntimeEntityId(runtimeEntityId);
		packet.setItem(item(ContainerId.INVENTORY, slot).orElse(ItemData.AIR));
		packet.setInventorySlot(slot);
		packet.setHotbarSlot(slot);
		packet.setContainerId(ContainerId.INVENTORY);
		return packet;
	}

	private void select(int slot) {
		if (slot >= 0 && slot < HOTBAR_SIZE) selected = slot;
	}

	private void close(int containerId) {
		if (!opened.remove(containerId)) return;

		containers.remove(containerId);
	}

	private void replace(int containerId, List<ItemData> contents) {
		if (!tracked(containerId)) return;

		containers.remove(containerId);
		for (int slot = 0; slot < contents.size(); slot++)
			put(new Slot(containerId, slot), contents.get(slot));
	}

	private void apply(InventoryActionData action) {
		InventorySource source = action.getSource();
		if (source.getType() != InventorySource.Type.CONTAINER || !tracked(source.getContainerId())) return;

		put(new Slot(source.getContainerId(), action.getSlot()), action.getToItem());
	}

	private void predict(ItemStackRequest request) {
		Map<Slot, ItemData> before = new HashMap<>();
		predictions.put(request.getRequestId(), before);
		for (ItemStackRequestAction action : request.getActions()) {
			switch (action) {
				case TransferItemStackRequestAction transfer -> transfer(request.getRequestId(), before, transfer);
				case SwapAction swap -> swap(request.getRequestId(), before, swap);
				case DropAction drop -> remove(request.getRequestId(), before, slot(drop.getSource()), drop.getCount());
				case DestroyAction destroy -> remove(request.getRequestId(), before, slot(destroy.getSource()), destroy.getCount());
				default -> { }
			}
		}
	}

	private void transfer(int requestId, Map<Slot, ItemData> before, TransferItemStackRequestAction transfer) {
		Slot source = slot(transfer.getSource());
		Slot destination = slot(transfer.getDestination());
		if (source == null || destination == null) return;

		ItemData moved = stack(source);
		if (moved == null) return;

		ItemData target = stack(destination);
		int count = target == null ? transfer.getCount() : target.getCount() + transfer.getCount();
		remove(requestId, before, source, transfer.getCount());
		change(before, destination, predicted(moved, count, requestId));
	}

	private void swap(int requestId, Map<Slot, ItemData> before, SwapAction swap) {
		Slot source = slot(swap.getSource());
		Slot destination = slot(swap.getDestination());
		if (source == null || destination == null) return;

		ItemData first = stack(source);
		ItemData second = stack(destination);
		change(before, source, second == null ? null : predicted(second, second.getCount(), requestId));
		change(before, destination, first == null ? null : predicted(first, first.getCount(), requestId));
	}

	private void remove(int requestId, Map<Slot, ItemData> before, @Nullable Slot slot, int count) {
		if (slot == null) return;

		ItemData stack = stack(slot);
		if (stack == null) return;

		int remaining = stack.getCount() - count;
		change(before, slot, remaining > 0 ? predicted(stack, remaining, requestId) : null);
	}

	private void change(Map<Slot, ItemData> before, Slot slot, @Nullable ItemData stack) {
		ItemData current = stack(slot);
		before.putIfAbsent(slot, current == null ? ItemData.AIR : current);
		put(slot, stack);
	}

	/**
	 * Settles the prediction of a request: the server names the stacks of an accepted one and rejects the others.
	 * The slot type of protocols from 712 on is in the container name, that of older ones in the container.
	 */
	@SuppressWarnings("deprecation")
	private void respond(ItemStackResponse response) {
		Map<Slot, ItemData> before = predictions.remove(response.getRequestId());
		if (response.getResult() != ItemStackResponseStatus.OK) {
			if (before != null) before.forEach(this::put);
			return;
		}

		for (ItemStackResponseContainer container : response.getContainers()) {
			ContainerSlotType type = container.getContainerName() == null ? container.getContainer() : container.getContainerName().getContainer();
			for (ItemStackResponseSlot confirmed : container.getItems())
				confirm(containerId(type), confirmed);
		}
	}

	/**
	 * Gives a slot the count and stack network ID the server's response names for it.
	 */
	private void confirm(int containerId, ItemStackResponseSlot confirmed) {
		if (containerId < 0) return;

		Slot slot = new Slot(containerId, confirmed.getSlot());
		ItemData stack = stack(slot);
		if (confirmed.getCount() <= 0) {
			put(slot, null);
			return;
		}
		if (stack == null) return;

		put(slot, stack.toBuilder()
				.count(confirmed.getCount())
				.usingNetId(true)
				.netId(confirmed.getStackNetworkId())
				.build());
	}

	@SuppressWarnings("deprecation")
	private @Nullable Slot slot(ItemStackRequestSlotData data) {
		ContainerSlotType type = data.getContainerName() == null ? data.getContainer() : data.getContainerName().getContainer();
		int containerId = containerId(type);
		return containerId < 0 ? null : new Slot(containerId, data.getSlot());
	}

	/**
	 * Maps the slot type of an item stack request or response to the container that holds it: the player's own
	 * containers, the cursor of the user interface, and the storage of the container the server opened last.
	 *
	 * @return the container ID, or {@code -1} for a slot type the client does not follow
	 */
	private int containerId(@Nullable ContainerSlotType type) {
		if (type == null) return -1;

		return switch (type) {
			case HOTBAR, INVENTORY, HOTBAR_AND_INVENTORY -> ContainerId.INVENTORY;
			case OFFHAND -> ContainerId.OFFHAND;
			case ARMOR -> ContainerId.ARMOR;
			case CURSOR -> ContainerId.UI;
			case LEVEL_ENTITY, BARREL, SHULKER_BOX -> opened.isEmpty() ? -1 : opened.getLast();
			default -> -1;
		};
	}

	private @Nullable ItemData stack(Slot slot) {
		Map<Integer, ItemData> container = containers.get(slot.containerId());
		return container == null ? null : container.get(slot.slot());
	}

	private void put(Slot slot, @Nullable ItemData stack) {
		if (stack == null || !stack.isValid()) {
			Map<Integer, ItemData> container = containers.get(slot.containerId());
			if (container != null) container.remove(slot.slot());
			return;
		}

		containers.computeIfAbsent(slot.containerId(), id -> new HashMap<>()).put(slot.slot(), stack);
	}

	private boolean tracked(int containerId) {
		return switch (containerId) {
			case ContainerId.INVENTORY, ContainerId.OFFHAND, ContainerId.ARMOR, ContainerId.UI -> true;
			default -> opened.contains(containerId);
		};
	}

	private static boolean serverContainer(int containerId) {
		return containerId >= ContainerId.FIRST && containerId <= ContainerId.LAST;
	}

	/**
	 * Returns a stack of the given count that the client names by its request ID until the server names it.
	 */
	private static ItemData predicted(ItemData stack, int count, int requestId) {
		return stack.toBuilder()
				.count(count)
				.usingNetId(true)
				.netId(requestId)
				.build();
	}

	/**
	 * One slot of one container.
	 */
	private record Slot(int containerId, int slot) {
	}
}
