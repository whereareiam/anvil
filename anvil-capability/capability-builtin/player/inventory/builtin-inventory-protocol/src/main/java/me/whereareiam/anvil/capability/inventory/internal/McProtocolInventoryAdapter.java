package me.whereareiam.anvil.capability.inventory.internal;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import org.geysermc.mcprotocollib.network.ClientSession;
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
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Owns native inventory packets and each player's observed container state.
 */
@RequiredArgsConstructor
public final class McProtocolInventoryAdapter {
	private final @NotNull Supplier<ClientSession> sessions;
	private final State state = new State();

	public void select(int slot) { sessions.get().send(new ServerboundSetCarriedItemPacket(slot)); }

	public @Nullable InventorySnapshot receive(@NotNull Packet packet) {
		switch (packet) {
			case ClientboundOpenScreenPacket open -> {
				state.containerId = open.getContainerId();
				state.stateId = 0;
				state.items.clear();
				return snapshot();
			}
			case ClientboundContainerSetContentPacket content -> {
				state.containerId = content.getContainerId();
				state.stateId = content.getStateId();
				state.items.clear();
				Collections.addAll(state.items, content.getItems());
				return snapshot();
			}
			case ClientboundContainerSetSlotPacket slot -> {
				if (slot.getContainerId() != state.containerId || slot.getSlot() < 0)
					return null;
				state.stateId = slot.getStateId();
				grow(state.items, slot.getSlot());
				state.items.set(slot.getSlot(), slot.getItem());
				return snapshot();
			}
			case ClientboundSetPlayerInventoryPacket slot -> {
				grow(state.items, slot.getSlot());
				state.items.set(slot.getSlot(), slot.getContents());
				return snapshot();
			}
			default -> {
			}
		}

		return null;
	}

	public void click(int slot, @NotNull InventoryClick click, int button) {
		ContainerActionType type;
		ContainerAction action;
		switch (click) {
			case LEFT -> {
				type = ContainerActionType.CLICK_ITEM;
				action = ClickItemAction.LEFT_CLICK;
			}
			case RIGHT -> {
				type = ContainerActionType.CLICK_ITEM;
				action = ClickItemAction.RIGHT_CLICK;
			}
			case SHIFT_LEFT -> {
				type = ContainerActionType.SHIFT_CLICK_ITEM;
				action = ShiftClickItemAction.LEFT_CLICK;
			}
			case HOTBAR_SWAP -> {
				type = ContainerActionType.MOVE_TO_HOTBAR_SLOT;
				action = MoveToHotbarAction.from(button);
				if (action == null)
					throw new IllegalArgumentException("Invalid hotbar button: " + button);
			}
			case DROP_ONE -> {
				type = ContainerActionType.DROP_ITEM;
				action = DropItemAction.DROP_FROM_SELECTED;
			}
			case DROP_STACK -> {
				type = ContainerActionType.DROP_ITEM;
				action = DropItemAction.DROP_SELECTED_STACK;
			}
			default -> throw new IllegalArgumentException("Unsupported inventory click: " + click);
		}
		sessions.get().send(new ServerboundContainerClickPacket(
				state.containerId,
				state.stateId,
				slot,
				type,
				action,
				null,
				Collections.emptyMap()
		));
	}

	private void grow(List<ItemStack> items, int slot) {
		while (items.size() <= slot)
			items.add(null);
	}

	private InventorySnapshot snapshot() {
		var snapshot = InventorySnapshot.builder()
				.containerId(state.containerId)
				.stateId(state.stateId);

		for (int slot = 0; slot < state.items.size(); slot++) {
			ItemStack item = state.items.get(slot);
			if (item == null) continue;

			snapshot.item(InventoryItem.builder()
					.slot(slot)
					.protocolId(item.getId())
					.amount(item.getAmount())
					.build());
		}

		return snapshot.build();
	}

	private static final class State {
		private int containerId;
		private int stateId;
		private final List<ItemStack> items = new ArrayList<>();
	}
}
