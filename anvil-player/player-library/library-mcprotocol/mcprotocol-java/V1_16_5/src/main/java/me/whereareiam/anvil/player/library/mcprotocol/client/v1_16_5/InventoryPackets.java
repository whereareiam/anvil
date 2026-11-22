package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.window.WindowAction;
import com.github.steveice10.mc.protocol.data.game.window.WindowActionParam;
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientConfirmTransactionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientWindowActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerConfirmTransactionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerAck;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerSetContent;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerSetSlot;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ServerboundContainerAck;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ServerboundContainerClick;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.player.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Containers of the MCProtocolLib 1.16.5 release, which has no state IDs yet: the translation reports every container
 * at state 0 and with no carried stack, which these packets do not name. A click carries an action number of its own
 * instead of the state ID, and the stack it picks up, which the client does not predict; the server then rejects the
 * click after performing it and resends the container. The server's confirmation of an action arrives as
 * {@link ClientboundContainerAck}, which the worker kit's client acknowledges as the vanilla client does, and an
 * acknowledgement leaves as the release's transaction confirmation.
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

	InventoryPackets(@NotNull ComponentTranslator text, @NotNull ItemTranslator items) {
		super(text, items);
	}

	@Override
	protected @NotNull Set<Class<? extends JavaPacket>> releasePackets() {
		return Set.of(ClientboundContainerAck.class, ServerboundContainerAck.class);
	}

	@Override
	protected @Nullable ClientboundPacket decodeRelease(@NotNull Packet packet) {
		if (packet instanceof ServerConfirmTransactionPacket confirmation)
			return new ClientboundContainerAck(confirmation.getWindowId(), (short) confirmation.getActionId(),
					confirmation.isAccepted());
		if (packet instanceof ServerWindowItemsPacket content)
			return new ClientboundContainerSetContent(content.getWindowId(), NO_STATE,
					items.items(content.getItems()), null);
		if (!(packet instanceof ServerSetSlotPacket slot)) return null;

		// The library reads the window ID as an unsigned byte; vanilla sends a signed one, so the cursor's -1 and the
		// inventory index container's -2 arrive as 255 and 254.
		int containerId = (byte) slot.getWindowId();
		if (containerId == INVENTORY_INDEX_CONTAINER)
			return new ClientboundSetPlayerInventory(slot.getSlot(), items.item(slot.getItem()));

		return new ClientboundContainerSetSlot(containerId, NO_STATE, slot.getSlot(),
				items.item(slot.getItem()));
	}

	@Override
	protected @Nullable Packet encodeRelease(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundContainerAck ack)) return null;

		return new ClientConfirmTransactionPacket(ack.getContainerId(), ack.getUid(), ack.isAccepted());
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
