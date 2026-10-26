package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetHeldSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.java.api.type.ClickType;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
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
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Containers and the held item of the MCProtocolLib releases from 1.20.5 on, which open and fill containers alike. Each
 * release's {@code InventoryPackets} reads the slot packets, which address the player's own inventory differently
 * between releases, and the held slot the server selects, whose packet Minecraft 1.21.2 renamed, and builds the click packet, whose stacks change between releases; the click's action, which the
 * library looks up by click type and button, does not.
 *
 * <p>The library names menu types as the game's registry does, so their keys are the lowercase constant names in the
 * {@code minecraft} namespace; only the cartography table's menu has a shorter constant.</p>
 */
public abstract class InventoryTranslation implements PacketTranslation<Packet> {
	private static final String CARTOGRAPHY_TABLE = "cartography_table";

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundOpenScreen.class,
				ClientboundContainerSetContent.class,
				ClientboundContainerSetSlot.class,
				ClientboundSetPlayerInventory.class,
				ClientboundSetHeldSlot.class,
				ServerboundSetCarriedItem.class,
				ServerboundContainerClick.class
		);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundOpenScreenPacket open)
			return new ClientboundOpenScreen(open.getContainerId(), menuType(open.getType()),
					ValueTranslator.plain(open.getTitle()));
		if (packet instanceof ClientboundContainerSetContentPacket content)
			return new ClientboundContainerSetContent(content.getContainerId(), content.getStateId(),
					ValueTranslator.items(content.getItems()), ValueTranslator.item(content.getCarriedItem()));

		return decodeSlot(packet);
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundSetCarriedItem carried)
			return new ServerboundSetCarriedItemPacket(carried.getSlot());
		if (packet instanceof ServerboundContainerClick click) return click(click, type(click), action(click));

		return null;
	}

	/**
	 * Reads a packet that changes one slot: of a container, of the cursor or of the player's own inventory, or the
	 * hotbar slot the player holds.
	 *
	 * @param packet a packet the release received
	 * @return the protocol's packet, or {@code null} when the packet changes no slot
	 */
	protected abstract @Nullable ClientboundPacket decodeSlot(@NotNull Packet packet);

	/**
	 * Builds the release's click packet, which predicts no changed slots and no carried stack.
	 *
	 * @param click the click to send
	 * @param type the library's type of the click
	 * @param action the library's action of the click
	 * @return the release's click packet
	 */
	protected abstract @NotNull Packet click(
			@NotNull ServerboundContainerClick click,
			@NotNull ContainerActionType type,
			@NotNull ContainerAction action
	);

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
		boolean primaryOrSecondary = button == ClickType.PRIMARY_BUTTON || button == ClickType.SECONDARY_BUTTON;
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
