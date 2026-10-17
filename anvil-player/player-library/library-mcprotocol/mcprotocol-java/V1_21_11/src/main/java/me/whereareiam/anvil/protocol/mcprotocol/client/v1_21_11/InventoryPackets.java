package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.mcprotocol.client.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.CreativeGrabAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.FillStackAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.SpreadItemAction;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Containers and the held item of Minecraft 1.21.11, whose server addresses the player's own inventory with a packet
 * of its own and whose clicks describe items as hashed stacks. The class also links against the 26.1 release.
 */
final class InventoryPackets implements PacketTranslation<Packet> {
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
					AdventureText.plain(open.getTitle()));
		if (packet instanceof ClientboundContainerSetContentPacket content)
			return new ClientboundContainerSetContent(content.getContainerId(), content.getStateId(),
					items(content.getItems()), item(content.getCarriedItem()));
		if (packet instanceof ClientboundContainerSetSlotPacket slot)
			return new ClientboundContainerSetSlot(slot.getContainerId(), slot.getStateId(), slot.getSlot(),
					item(slot.getItem()));
		if (packet instanceof ClientboundSetPlayerInventoryPacket inventory)
			return new ClientboundSetPlayerInventory(inventory.getSlot(), item(inventory.getContents()));

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

	private static List<ItemStack> items(org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack[] stacks) {
		List<ItemStack> items = new ArrayList<>(stacks.length);
		for (var stack : stacks) items.add(item(stack));
		return Collections.unmodifiableList(items);
	}

	private static @Nullable ItemStack item(
			org.geysermc.mcprotocollib.protocol.data.game.item.@Nullable ItemStack stack
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
		boolean primaryOrSecondary = button == 0 || button == 1;
		ContainerAction action = switch (click.getClickType()) {
			case PICKUP -> primaryOrSecondary ? ClickItemAction.from(button) : null;
			case QUICK_MOVE -> primaryOrSecondary ? ShiftClickItemAction.from(button) : null;
			case SWAP -> MoveToHotbarAction.from(button);
			case CLONE -> CreativeGrabAction.GRAB;
			case THROW -> primaryOrSecondary ? DropItemAction.from(button) : null;
			case QUICK_CRAFT -> SpreadItemAction.from(button);
			case PICKUP_ALL -> FillStackAction.FILL;
		};
		if (action == null)
			throw new IllegalArgumentException("Invalid button " + button + " for a " + click.getClickType()
					+ " click");

		return action;
	}
}
