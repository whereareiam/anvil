package me.whereareiam.anvil.capability.inventory.internal;

import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolWorkerPlayer;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Installs inventory packet observation and interaction into an MCProtocol worker.
 */
public final class McProtocolInventoryAdapter implements ProtocolCapabilityAdapter {
	@Override
	public @NotNull String id() {
		return McProtocolInventoryProvider.ID;
	}

	@Override
	public boolean supports(int protocolNumber) {
		return McProtocolInventoryAdapterProvider.supportsProtocol(protocolNumber);
	}

	@Override
	public void install(@NotNull ProtocolCapabilityAdapterRegistry registry) {
		registry.operation("inventory.select", (player, arguments) -> {
			player.send(new ServerboundSetCarriedItemPacket(arguments.path("slot").asInt()));
			return player.mapper().createObjectNode();
		});
		registry.operation("inventory.click", (player, arguments) -> {
			click(player, arguments.path("slot").asInt(), arguments.path("click").asText(),
					arguments.path("button").asInt());
			return player.mapper().createObjectNode();
		});
		registry.packets((player, packet) -> {
			State state = player.state(State.class, State::new);
			if (packet instanceof ClientboundOpenScreenPacket open) {
				state.containerId = open.getContainerId();
				state.stateId = 0;
				state.items.clear();
				emit(player, state);
				return;
			}
			if (packet instanceof ClientboundContainerSetContentPacket content) {
				state.containerId = content.getContainerId();
				state.stateId = content.getStateId();
				state.items.clear();
				Collections.addAll(state.items, content.getItems());
				emit(player, state);
				return;
			}
			if (packet instanceof ClientboundContainerSetSlotPacket slot) {
				if (slot.getContainerId() != state.containerId || slot.getSlot() < 0)
					return;
				state.stateId = slot.getStateId();
				grow(state.items, slot.getSlot());
				state.items.set(slot.getSlot(), slot.getItem());
				emit(player, state);
				return;
			}
			if (packet instanceof ClientboundSetPlayerInventoryPacket slot) {
				grow(state.items, slot.getSlot());
				state.items.set(slot.getSlot(), slot.getContents());
				emit(player, state);
			}
		});
	}

	private void click(ProtocolWorkerPlayer player, int slot, String click, int button) {
		State state = player.state(State.class, State::new);
		ContainerActionType type;
		ContainerAction action;
		switch (click) {
			case "LEFT" -> {
				type = ContainerActionType.CLICK_ITEM;
				action = ClickItemAction.LEFT_CLICK;
			}
			case "RIGHT" -> {
				type = ContainerActionType.CLICK_ITEM;
				action = ClickItemAction.RIGHT_CLICK;
			}
			case "SHIFT_LEFT" -> {
				type = ContainerActionType.SHIFT_CLICK_ITEM;
				action = ShiftClickItemAction.LEFT_CLICK;
			}
			case "HOTBAR_SWAP" -> {
				type = ContainerActionType.MOVE_TO_HOTBAR_SLOT;
				action = MoveToHotbarAction.from(button);
				if (action == null)
					throw new IllegalArgumentException("Invalid hotbar button: " + button);
			}
			case "DROP_ONE" -> {
				type = ContainerActionType.DROP_ITEM;
				action = DropItemAction.DROP_FROM_SELECTED;
			}
			case "DROP_STACK" -> {
				type = ContainerActionType.DROP_ITEM;
				action = DropItemAction.DROP_SELECTED_STACK;
			}
			default -> throw new IllegalArgumentException("Unsupported inventory click: " + click);
		}
		player.send(new ServerboundContainerClickPacket(
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

	private void emit(ProtocolWorkerPlayer player, State state) {
		player.emit("inventory.changed", payload -> {
			payload.put("containerId", state.containerId);
			payload.put("stateId", state.stateId);
			var items = payload.putArray("items");
			for (int slot = 0; slot < state.items.size(); slot++) {
				ItemStack item = state.items.get(slot);
				if (item == null)
					continue;
				var value = items.addObject();
				value.put("slot", slot);
				value.put("protocolId", item.getId());
				value.put("amount", item.getAmount());
			}
		});
	}

	private static final class State {
		private int containerId;
		private int stateId;
		private final List<ItemStack> items = new ArrayList<>();
	}
}
