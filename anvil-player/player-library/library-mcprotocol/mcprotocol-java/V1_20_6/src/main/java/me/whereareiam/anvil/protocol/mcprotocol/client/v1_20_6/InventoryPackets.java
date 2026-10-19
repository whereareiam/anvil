package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetHeldSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Containers and the held item of the MCProtocolLib 1.20.6 and 1.21.1 releases. The server addresses the player's own
 * inventory through the slot packet of a container of its own, which the translation reports as {@link
 * ClientboundSetPlayerInventory}, and clicks carry the clicked stack as a full item stack.
 */
public final class InventoryPackets extends InventoryTranslation {
	/**
	 * The container whose slot packet addresses a slot of the player's inventory by its inventory index.
	 */
	private static final int INVENTORY_INDEX_CONTAINER = -2;

	/**
	 * Reads a slot packet or the held slot. The library reads its container ID as an unsigned byte; vanilla sends a signed one, so the
	 * cursor's {@code -1} and the inventory index container's {@code -2} arrive as 255 and 254.
	 */
	@Override
	protected @Nullable ClientboundPacket decodeSlot(@NotNull Packet packet) {
		if (packet instanceof ClientboundSetCarriedItemPacket held) return new ClientboundSetHeldSlot(held.getSlot());
		if (!(packet instanceof ClientboundContainerSetSlotPacket slot)) return null;

		int containerId = (byte) slot.getContainerId();
		if (containerId == INVENTORY_INDEX_CONTAINER)
			return new ClientboundSetPlayerInventory(slot.getSlot(), ValueTranslator.item(slot.getItem()));

		return new ClientboundContainerSetSlot(containerId, slot.getStateId(), slot.getSlot(),
				ValueTranslator.item(slot.getItem()));
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
