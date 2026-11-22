package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.window.WindowAction;
import com.github.steveice10.mc.protocol.data.game.window.WindowActionParam;
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientWindowActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerSetContent;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerSetSlot;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ServerboundContainerClick;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.player.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.InventoryTranslation;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ItemTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ValueTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Containers of the MCProtocolLib 1.17.1 release, which carry the state IDs Minecraft 1.17 introduced, and a click
 * carries the container's state ID and no predicted slots, so the server confirms nothing and the release carries no
 * packet of its own in the area.
 */
final class InventoryPackets extends InventoryTranslation {
	InventoryPackets(@NotNull ComponentTranslator text, @NotNull ItemTranslator items) {
		super(text, items);
	}

	@Override
	protected @NotNull Set<Class<? extends JavaPacket>> releasePackets() {
		return Set.of();
	}

	@Override
	protected @Nullable ClientboundPacket decodeRelease(@NotNull Packet packet) {
		if (packet instanceof ServerWindowItemsPacket content)
			return new ClientboundContainerSetContent(content.getWindowId(), content.getStateId(),
					items.items(content.getItems()), items.item(content.getCarriedItem()));
		if (!(packet instanceof ServerSetSlotPacket slot)) return null;

		// The library reads the window ID as an unsigned byte; vanilla sends a signed one, so the cursor's -1 and the
		// inventory index container's -2 arrive as 255 and 254.
		int containerId = (byte) slot.getWindowId();
		if (containerId == INVENTORY_INDEX_CONTAINER)
			return new ClientboundSetPlayerInventory(slot.getSlot(), items.item(slot.getItem()));

		return new ClientboundContainerSetSlot(containerId, slot.getStateId(), slot.getSlot(),
				items.item(slot.getItem()));
	}

	@Override
	protected @Nullable Packet encodeRelease(@NotNull ServerboundPacket packet) {
		return null;
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
