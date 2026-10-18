package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.inventory.ClickItemAction;
import com.github.steveice10.mc.protocol.data.game.inventory.ContainerAction;
import com.github.steveice10.mc.protocol.data.game.inventory.ContainerActionType;
import com.github.steveice10.mc.protocol.data.game.inventory.ContainerType;
import com.github.steveice10.mc.protocol.data.game.inventory.CreativeGrabAction;
import com.github.steveice10.mc.protocol.data.game.inventory.DropItemAction;
import com.github.steveice10.mc.protocol.data.game.inventory.FillStackAction;
import com.github.steveice10.mc.protocol.data.game.inventory.MoveToHotbarAction;
import com.github.steveice10.mc.protocol.data.game.inventory.ShiftClickItemAction;
import com.github.steveice10.mc.protocol.data.game.inventory.SpreadItemAction;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.PlayerInventoryLayout;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Containers and the held item of the MCProtocolLib 1.18.2 release. The server addresses the player's own inventory
 * through the slot packet of a container of its own, which the translation reports as
 * {@link ClientboundSetPlayerInventory}, and clicks carry the action the library derives from the click type and
 * button. Its click actions have no lookup by button, so the button indexes each action's constants.
 *
 * <p>The library names menu types as the game's registry does, so their keys are the lowercase constant names in the
 * {@code minecraft} namespace; only the cartography table's menu has a shorter constant.</p>
 */
public final class InventoryPackets implements PacketTranslation<Packet> {
	/**
	 * The container whose slot packet addresses a slot of the player's inventory by its inventory index.
	 */
	private static final int INVENTORY_INDEX_CONTAINER = -2;
	/**
	 * Every fourth drag button is no button: 0 to 2 drag with the left button, 4 to 6 with the right and 8 to 10 with
	 * the middle one.
	 */
	private static final int DRAG_BUTTONS = 4;
	private static final String CARTOGRAPHY_TABLE = "cartography_table";
	private static final MoveToHotbarAction[] HOTBAR = {
			MoveToHotbarAction.SLOT_1, MoveToHotbarAction.SLOT_2, MoveToHotbarAction.SLOT_3,
			MoveToHotbarAction.SLOT_4, MoveToHotbarAction.SLOT_5, MoveToHotbarAction.SLOT_6,
			MoveToHotbarAction.SLOT_7, MoveToHotbarAction.SLOT_8, MoveToHotbarAction.SLOT_9
	};
	private static final DropItemAction[] DROPS = {
			DropItemAction.DROP_FROM_SELECTED, DropItemAction.DROP_SELECTED_STACK
	};

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundOpenScreen.class,
				ClientboundContainerSetContent.class,
				ClientboundContainerSetSlot.class,
				ClientboundSetPlayerInventory.class,
				ServerboundSetCarriedItem.class,
				ServerboundContainerClick.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundOpenScreenPacket open)
			return new ClientboundOpenScreen(open.getContainerId(), menuType(open.getType()),
					ValueTranslator.plain(open.getTitle()));
		if (packet instanceof ClientboundContainerSetContentPacket content)
			return new ClientboundContainerSetContent(content.getContainerId(), content.getStateId(),
					ValueTranslator.items(content.getItems()), ValueTranslator.item(content.getCarriedItem()));
		if (packet instanceof ClientboundContainerSetSlotPacket slot) return slot(slot);

		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundSetCarriedItem carried)
			return new ServerboundSetCarriedItemPacket(carried.getSlot());
		if (!(packet instanceof ServerboundContainerClick click)) return null;

		return new ServerboundContainerClickPacket(click.getContainerId(), click.getStateId(), click.getSlot(),
				type(click), action(click), null, Map.of());
	}

	/**
	 * Reads a slot packet. The library reads its container ID as an unsigned byte; vanilla sends a signed one, so the
	 * cursor's {@code -1} and the inventory index container's {@code -2} arrive as 255 and 254.
	 */
	private static ClientboundPacket slot(ClientboundContainerSetSlotPacket slot) {
		int containerId = (byte) slot.getContainerId();
		if (containerId == INVENTORY_INDEX_CONTAINER)
			return new ClientboundSetPlayerInventory(slot.getSlot(), ValueTranslator.item(slot.getItem()));

		return new ClientboundContainerSetSlot(containerId, slot.getStateId(), slot.getSlot(),
				ValueTranslator.item(slot.getItem()));
	}

	private static String menuType(ContainerType type) {
		String path = type == ContainerType.CARTOGRAPHY ? CARTOGRAPHY_TABLE : type.name().toLowerCase(Locale.ROOT);
		return Key.key(Key.MINECRAFT_NAMESPACE, path).asString();
	}

	private static ContainerActionType type(ServerboundContainerClick click) {
		return switch (click.getClickType()) {
			case PICKUP -> ContainerActionType.CLICK_ITEM;
			case QUICK_MOVE -> ContainerActionType.SHIFT_CLICK_ITEM;
			case SWAP -> ContainerActionType.MOVE_TO_HOTBAR_SLOT;
			case CLONE -> ContainerActionType.CREATIVE_GRAB_MAX_STACK;
			case THROW -> ContainerActionType.DROP_ITEM;
			case QUICK_CRAFT -> ContainerActionType.SPREAD_ITEM;
			case PICKUP_ALL -> ContainerActionType.FILL_STACK;
		};
	}

	private static ContainerAction action(ServerboundContainerClick click) {
		int button = click.getButton();
		return switch (click.getClickType()) {
			case PICKUP -> constant(ClickItemAction.values(), button, click);
			case QUICK_MOVE -> constant(ShiftClickItemAction.values(), button, click);
			case SWAP -> button == PlayerInventoryLayout.OFF_HAND ? MoveToHotbarAction.OFF_HAND
					: constant(HOTBAR, button, click);
			case CLONE -> CreativeGrabAction.GRAB;
			case THROW -> constant(DROPS, button, click);
			case QUICK_CRAFT -> button % DRAG_BUTTONS == DRAG_BUTTONS - 1
					? invalid(click)
					: constant(SpreadItemAction.values(), button - button / DRAG_BUTTONS, click);
			case PICKUP_ALL -> FillStackAction.FILL;
		};
	}

	private static <A extends ContainerAction> A constant(A[] actions, int index, ServerboundContainerClick click) {
		if (index < 0 || index >= actions.length) return invalid(click);

		return actions[index];
	}

	private static <A> A invalid(ServerboundContainerClick click) {
		throw new IllegalArgumentException("Invalid button " + click.getButton() + " for a " + click.getClickType()
				+ " click");
	}
}
