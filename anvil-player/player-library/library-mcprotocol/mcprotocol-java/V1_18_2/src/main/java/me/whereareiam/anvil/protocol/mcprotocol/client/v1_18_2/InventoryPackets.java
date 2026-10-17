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
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.mcprotocol.client.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Containers and the held item of Minecraft 1.18.2. The server addresses the player's own inventory through the slot
 * packet of container {@code -2}, and clicks carry the action the library derives from the click type and button.
 * Its click actions have no lookup by button, so the button indexes each action's constants.
 */
final class InventoryPackets implements PacketTranslation<Packet> {
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
				ServerboundSetCarriedItem.class,
				ServerboundContainerClick.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundOpenScreenPacket open)
			return new ClientboundOpenScreen(open.getContainerId(), menuType(open.getType()),
					AdventureText.plain(open.getTitle()));
		if (packet instanceof ClientboundContainerSetContentPacket content)
			return new ClientboundContainerSetContent(content.getContainerId(), content.getStateId(),
					items(content.getItems()), item(content.getCarriedItem()));
		if (packet instanceof ClientboundContainerSetSlotPacket slot)
			// The library reads this container ID as an unsigned byte; vanilla sends a signed one, so the cursor's -1
			// and the inventory index container's -2 arrive as 255 and 254.
			return new ClientboundContainerSetSlot((byte) slot.getContainerId(), slot.getStateId(), slot.getSlot(),
					item(slot.getItem()));

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

	private static String menuType(ContainerType type) {
		if (type == ContainerType.CARTOGRAPHY) return "minecraft:cartography_table";

		return "minecraft:" + type.name().toLowerCase(Locale.ROOT);
	}

	private static List<ItemStack> items(
			com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack[] stacks
	) {
		List<ItemStack> items = new ArrayList<>(stacks.length);
		for (var stack : stacks) items.add(item(stack));
		return Collections.unmodifiableList(items);
	}

	private static @Nullable ItemStack item(
			com.github.steveice10.mc.protocol.data.game.entity.metadata.@Nullable ItemStack stack
	) {
		if (stack == null) return null;

		return new ItemStack(stack.getId(), stack.getAmount());
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
			case SWAP -> button == 40 ? MoveToHotbarAction.OFF_HAND : constant(HOTBAR, button, click);
			case CLONE -> CreativeGrabAction.GRAB;
			case THROW -> constant(DROPS, button, click);
			// The drag buttons skip every fourth number: 0..2 left, 4..6 right and 8..10 middle.
			case QUICK_CRAFT -> button % 4 == 3
					? invalid(click)
					: constant(SpreadItemAction.values(), button - button / 4, click);
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
