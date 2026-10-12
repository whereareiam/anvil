package me.whereareiam.anvil.capability.inventory.cloudburst.v1_26_60;

import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory packets of CloudburstMC Protocol releases from the one that serves Bedrock 1.26.60, for every codec the
 * release has. Bedrock has no state IDs, so every state ID is {@code 0}.
 *
 * <p>Bedrock keeps the player's own inventory in three containers: the inventory (container {@code 0}, hotbar
 * {@code 0..8} and main inventory {@code 9..35}), the off hand (container {@code 119}) and the armor (container
 * {@code 120}, head to feet). Their slots are reported as inventory indexes. Containers the server opens use the
 * IDs {@code 1..100} and are reported with the slots they arrive with; the cursor and the other fixed user-interface
 * containers are not reported.</p>
 *
 * <p>Selecting a hotbar slot and clicking a slot are refused: a Bedrock client equips a slot with a mob equipment
 * packet that carries its own copy of the slot's item, and moves items with item stack requests that name each
 * stack by the network ID the server assigned it. The CloudburstMC Protocol session does not keep the player's
 * item stacks, and a stateless segment cannot, so neither packet can be sent faithfully.</p>
 */
public final class CloudburstInventoryPackets implements InventoryPackets<CloudburstSession<BedrockPacket, ItemDefinition>> {
	private static final int INVENTORY_SIZE = 36;
	private static final int OFF_HAND_INDEX = 40;
	private static final int HEAD_INDEX = 39;
	private static final int ARMOR_SIZE = 4;

	@Override
	@SuppressWarnings("unchecked")
	public @NotNull Class<CloudburstSession<BedrockPacket, ItemDefinition>> sessionType() {
		return (Class<CloudburstSession<BedrockPacket, ItemDefinition>>) (Class<?>) CloudburstSession.class;
	}

	/**
	 * Refuses to select a hotbar slot, which a Bedrock client announces with the slot's item.
	 *
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public void selectHotbar(@NotNull CloudburstSession<BedrockPacket, ItemDefinition> session, int slot) {
		throw new UnsupportedOperationException("A Bedrock player selects a hotbar slot with a mob equipment packet that carries "
				+ "its own copy of the slot's item, which the CloudburstMC Protocol session does not keep yet");
	}

	/**
	 * Refuses to click a slot, which a Bedrock client sends as an item stack request.
	 *
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public void click(@NotNull CloudburstSession<BedrockPacket, ItemDefinition> session, @NotNull ContainerClick click) {
		throw new UnsupportedOperationException("A Bedrock player moves items with item stack requests that name each stack by "
				+ "its network ID and slot type, which the CloudburstMC Protocol session does not keep yet");
	}

	@Override
	public @NotNull Runnable listen(
			@NotNull CloudburstSession<BedrockPacket, ItemDefinition> session,
			@NotNull InventoryPacketListener listener
	) {
		List<PacketSubscription> subscriptions = List.of(
				session.subscribe(ContainerOpenPacket.class, open -> opened(open, listener)),
				session.subscribe(InventoryContentPacket.class, content -> contents(content, listener)),
				session.subscribe(InventorySlotPacket.class, slot -> slot(slot, listener))
		);
		return () -> subscriptions.forEach(PacketSubscription::close);
	}

	private static void opened(ContainerOpenPacket open, InventoryPacketListener listener) {
		// The player's own inventory is always observed; opening it brings no contents of its own.
		if (serverContainer(open.getId())) listener.opened(open.getId());
	}

	private static void contents(InventoryContentPacket content, InventoryPacketListener listener) {
		int containerId = content.getContainerId();
		List<ItemData> stacks = content.getContents();
		if (playerContainer(containerId)) {
			for (int slot = 0; slot < stacks.size(); slot++)
				playerSlot(containerId, slot, stacks.get(slot), listener);
			return;
		}
		if (!serverContainer(containerId)) return;

		List<InventoryItem> items = new ArrayList<>();
		for (int slot = 0; slot < stacks.size(); slot++) {
			InventoryItem item = item(slot, stacks.get(slot));
			if (item != null) items.add(item);
		}
		listener.contents(containerId, 0, items);
	}

	private static void slot(InventorySlotPacket packet, InventoryPacketListener listener) {
		int containerId = packet.getContainerId();
		if (playerContainer(containerId)) {
			playerSlot(containerId, packet.getSlot(), packet.getItem(), listener);
			return;
		}
		if (!serverContainer(containerId)) return;

		listener.slot(containerId, 0, packet.getSlot(), item(packet.getSlot(), packet.getItem()));
	}

	private static void playerSlot(int containerId, int slot, @Nullable ItemData stack, InventoryPacketListener listener) {
		int index = inventoryIndex(containerId, slot);
		if (index < 0) return;

		listener.playerSlot(index, item(index, stack));
	}

	/**
	 * Maps a slot of one of the player's own containers to its inventory index.
	 *
	 * @return the inventory index, or {@code -1} for a slot the container does not have
	 */
	private static int inventoryIndex(int containerId, int slot) {
		if (slot < 0) return -1;

		return switch (containerId) {
			case ContainerId.INVENTORY -> slot < INVENTORY_SIZE ? slot : -1;
			case ContainerId.OFFHAND -> slot == 0 ? OFF_HAND_INDEX : -1;
			case ContainerId.ARMOR -> slot < ARMOR_SIZE ? HEAD_INDEX - slot : -1;
			default -> -1;
		};
	}

	private static boolean playerContainer(int containerId) {
		return containerId == ContainerId.INVENTORY || containerId == ContainerId.OFFHAND || containerId == ContainerId.ARMOR;
	}

	private static boolean serverContainer(int containerId) {
		return containerId >= ContainerId.FIRST && containerId <= ContainerId.LAST;
	}

	private static @Nullable InventoryItem item(int slot, @Nullable ItemData stack) {
		if (stack == null || !stack.isValid()) return null;

		ItemDefinition definition = stack.getDefinition();
		return InventoryItem.builder()
				.slot(slot)
				.protocolId(definition.getRuntimeId())
				.amount(stack.getCount())
				.identifier(definition.getIdentifier())
				.build();
	}
}
