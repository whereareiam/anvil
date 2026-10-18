package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.InventoryTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ValueTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Containers and the held item of the MCProtocolLib 1.21.11 release, whose server addresses the player's own
 * inventory with a packet of its own and whose clicks describe items as hashed stacks.
 */
public final class InventoryPackets extends InventoryTranslation {
	@Override
	protected @Nullable ClientboundPacket decodeSlot(@NotNull Packet packet) {
		if (packet instanceof ClientboundContainerSetSlotPacket slot)
			return new ClientboundContainerSetSlot(slot.getContainerId(), slot.getStateId(), slot.getSlot(),
					ValueTranslator.item(slot.getItem()));
		if (packet instanceof ClientboundSetPlayerInventoryPacket inventory)
			return new ClientboundSetPlayerInventory(inventory.getSlot(),
					ValueTranslator.item(inventory.getContents()));

		return null;
	}

	@Override
	protected @NotNull Packet click(
			@NotNull ServerboundContainerClick click,
			@NotNull ContainerActionType type,
			@NotNull ContainerAction action
	) {
		return new ServerboundContainerClickPacket(click.getContainerId(), click.getStateId(), click.getSlot(), type,
				action, null, Map.of());
	}
}
