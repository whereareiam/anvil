package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.window.WindowAction;
import com.github.steveice10.mc.protocol.data.game.window.WindowActionParam;
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientWindowActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.InventoryTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.ValueTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Containers of the MCProtocolLib 1.17.1 release, which carry the state IDs Minecraft 1.17 introduced, and a click
 * carries the container's state ID and no predicted slots, so the server confirms nothing.
 */
final class InventoryPackets extends InventoryTranslation {
	@Override
	protected @Nullable ClientboundPacket decodeContents(@NotNull Packet packet) {
		if (packet instanceof ServerWindowItemsPacket content)
			return new ClientboundContainerSetContent(content.getWindowId(), content.getStateId(),
					ValueTranslator.items(content.getItems()), ValueTranslator.item(content.getCarriedItem()));
		if (!(packet instanceof ServerSetSlotPacket slot)) return null;

		// The library reads the window ID as an unsigned byte; vanilla sends a signed one, so the cursor's -1 and the
		// inventory index container's -2 arrive as 255 and 254.
		int containerId = (byte) slot.getWindowId();
		if (containerId == INVENTORY_INDEX_CONTAINER)
			return new ClientboundSetPlayerInventory(slot.getSlot(), ValueTranslator.item(slot.getItem()));

		return new ClientboundContainerSetSlot(containerId, slot.getStateId(), slot.getSlot(),
				ValueTranslator.item(slot.getItem()));
	}

	@Override
	protected @NotNull Packet click(
			@NotNull ServerboundContainerClick click,
			@NotNull WindowAction action,
			@NotNull WindowActionParam param
	) {
		return new ClientWindowActionPacket(click.getContainerId(), click.getStateId(), click.getSlot(), action, param,
				null, Map.of());
	}
}
