package me.whereareiam.anvil.capability.inventory.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.inventory.ClickItemAction;
import com.github.steveice10.mc.protocol.data.game.inventory.ContainerAction;
import com.github.steveice10.mc.protocol.data.game.inventory.ContainerActionType;
import com.github.steveice10.mc.protocol.data.game.inventory.DropItemAction;
import com.github.steveice10.mc.protocol.data.game.inventory.MoveToHotbarAction;
import com.github.steveice10.mc.protocol.data.game.inventory.ShiftClickItemAction;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.event.session.SessionListener;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPacketListener;
import me.whereareiam.anvil.capability.inventory.packet.InventoryPackets;
import me.whereareiam.anvil.capability.inventory.packet.model.ContainerClick;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory packets of MCProtocolLib releases from Minecraft 1.18.2, which name packets after the Mojang
 * mappings. Their hotbar actions have no lookup by button before 1.19.4, so the button indexes the actions, whose
 * first nine constants are the hotbar slots in order.
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
			case HOTBAR_SWAP -> hotbar(click.getButton());
			case DROP_ONE -> DropItemAction.DROP_FROM_SELECTED;
			case DROP_STACK -> DropItemAction.DROP_SELECTED_STACK;
		};
	}

	private static MoveToHotbarAction hotbar(int button) {
		if (button == ContainerClick.OFF_HAND_BUTTON) return MoveToHotbarAction.OFF_HAND;

		return MoveToHotbarAction.values()[button];
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
