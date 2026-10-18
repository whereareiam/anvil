package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Containers of the MCProtocolLib 1.16.5 release, which has no state IDs yet: the translation reports every container
 * at state 0 and with no carried stack, which these packets do not name. A click carries an action number of its own
 * instead of the state ID, and the stack it picks up, which the client does not predict; the server then rejects the
 * click after performing it and resends the container, and {@link ReleaseAnswers} acknowledges the rejection.
 */
final class InventoryPackets extends InventoryTranslation {
	/**
	 * The state ID the protocol reports for a release without state IDs.
	 */
	private static final int NO_STATE = 0;
	/**
	 * The action numbers a client counts, which the server echoes in its confirmation; vanilla counts them in a short.
	 */
	private static final int ACTION_NUMBERS = Short.MAX_VALUE;

	private final AtomicInteger actions = new AtomicInteger();

	@Override
	protected @Nullable ClientboundPacket decodeContents(@NotNull Packet packet) {
		if (packet instanceof ServerWindowItemsPacket content)
			return new ClientboundContainerSetContent(content.getWindowId(), NO_STATE,
					ValueTranslator.items(content.getItems()), null);
		if (!(packet instanceof ServerSetSlotPacket slot)) return null;

		// The library reads the window ID as an unsigned byte; vanilla sends a signed one, so the cursor's -1 and the
		// inventory index container's -2 arrive as 255 and 254.
		int containerId = (byte) slot.getWindowId();
		if (containerId == INVENTORY_INDEX_CONTAINER)
			return new ClientboundSetPlayerInventory(slot.getSlot(), ValueTranslator.item(slot.getItem()));

		return new ClientboundContainerSetSlot(containerId, NO_STATE, slot.getSlot(),
				ValueTranslator.item(slot.getItem()));
	}

	@Override
	protected @NotNull Packet click(
			@NotNull ServerboundContainerClick click,
			@NotNull WindowAction action,
			@NotNull WindowActionParam param
	) {
		int actionNumber = actions.updateAndGet(previous -> previous % ACTION_NUMBERS + 1);
		return new ClientWindowActionPacket(click.getContainerId(), actionNumber, click.getSlot(), null, action, param);
	}
}
