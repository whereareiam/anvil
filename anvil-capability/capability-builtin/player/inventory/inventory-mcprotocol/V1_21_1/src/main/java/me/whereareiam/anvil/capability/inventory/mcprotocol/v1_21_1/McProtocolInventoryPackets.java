package me.whereareiam.anvil.capability.inventory.mcprotocol.v1_21_1;

import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory packets of MCProtocolLib releases from Minecraft 1.21.1, which moved to the {@code org.geysermc}
 * packages: clicks describe items as item stacks, and the server addresses the player's own inventory through the
 * slot packet of container {@code -2}.
 */
public final class McProtocolInventoryPackets implements InventoryPackets<Session> {
	private static final int INVENTORY_INDEX_CONTAINER = -2;

	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void selectHotbar(@NotNull Session session, int slot) {
		session.send(new ServerboundSetCarriedItemPacket(slot));
	}

	@Override
	public void click(@NotNull Session session, @NotNull ContainerClick click) {
		session.send(new ServerboundContainerClickPacket(
				click.getContainerId(),
				click.getStateId(),
				click.getSlot(),
				type(click),
				action(click),
				null,
				Map.of()
		));
	}

	@Override
	public @NotNull Runnable listen(@NotNull Session session, @NotNull InventoryPacketListener listener) {
		SessionListener packets = new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (session.isConnected()) receive(packet, listener);
			}
		};
		session.addListener(packets);
		return () -> session.removeListener(packets);
	}

	private static void receive(Packet packet, InventoryPacketListener listener) {
		switch (packet) {
			case ClientboundOpenScreenPacket open -> listener.opened(open.getContainerId());
			case ClientboundContainerSetContentPacket content ->
					listener.contents(content.getContainerId(), content.getStateId(), items(content.getItems()));
			case ClientboundContainerSetSlotPacket slot -> slot(slot, listener);
			default -> {
			}
		}
	}

	private static void slot(ClientboundContainerSetSlotPacket packet, InventoryPacketListener listener) {
		// The library reads this container ID as an unsigned byte; vanilla sends a signed one, so the cursor's -1
		// and the inventory index container's -2 arrive as 255 and 254.
		int containerId = (byte) packet.getContainerId();
		InventoryItem item = item(packet.getSlot(), packet.getItem());
		if (containerId == INVENTORY_INDEX_CONTAINER) {
			listener.playerSlot(packet.getSlot(), item);
			return;
		}

		listener.slot(containerId, packet.getStateId(), packet.getSlot(), item);
	}

	private static ContainerActionType type(ContainerClick click) {
		return switch (click.getClick()) {
			case LEFT, RIGHT -> ContainerActionType.CLICK_ITEM;
			case SHIFT_LEFT -> ContainerActionType.SHIFT_CLICK_ITEM;
			case HOTBAR_SWAP -> ContainerActionType.MOVE_TO_HOTBAR_SLOT;
			case DROP_ONE, DROP_STACK -> ContainerActionType.DROP_ITEM;
		};
	}

	private static ContainerAction action(ContainerClick click) {
		return switch (click.getClick()) {
			case LEFT -> ClickItemAction.LEFT_CLICK;
			case RIGHT -> ClickItemAction.RIGHT_CLICK;
			case SHIFT_LEFT -> ShiftClickItemAction.LEFT_CLICK;
			case HOTBAR_SWAP -> MoveToHotbarAction.from(click.getButton());
			case DROP_ONE -> DropItemAction.DROP_FROM_SELECTED;
			case DROP_STACK -> DropItemAction.DROP_SELECTED_STACK;
		};
	}

	private static List<InventoryItem> items(ItemStack[] stacks) {
		List<InventoryItem> items = new ArrayList<>();
		for (int slot = 0; slot < stacks.length; slot++) {
			InventoryItem item = item(slot, stacks[slot]);
			if (item != null) items.add(item);
		}

		return items;
	}

	private static @Nullable InventoryItem item(int slot, @Nullable ItemStack stack) {
		if (stack == null) return null;

		return InventoryItem.builder()
				.slot(slot)
				.protocolId(stack.getId())
				.amount(stack.getAmount())
				.build();
	}
}
