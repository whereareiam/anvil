package me.whereareiam.anvil.capability.inventory.mcprotocol.v1_21_11;

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
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory packets of MCProtocolLib releases from Minecraft 1.21.11, whose clicks describe items as hashed stacks
 * and whose server addresses the player's own inventory with a separate packet.
 */
public final class McProtocolInventoryPackets implements InventoryPackets<Session> {
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
			case ClientboundContainerSetSlotPacket slot ->
					listener.slot(slot.getContainerId(), slot.getStateId(), slot.getSlot(), item(slot.getSlot(), slot.getItem()));
			case ClientboundSetPlayerInventoryPacket slot ->
					listener.playerSlot(slot.getSlot(), item(slot.getSlot(), slot.getContents()));
			default -> {
			}
		}
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
