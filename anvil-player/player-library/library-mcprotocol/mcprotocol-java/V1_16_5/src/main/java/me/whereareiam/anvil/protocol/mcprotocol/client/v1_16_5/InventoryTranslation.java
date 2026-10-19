package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.DefaultComponentSerializer;
import com.github.steveice10.mc.protocol.data.game.window.ClickItemParam;
import com.github.steveice10.mc.protocol.data.game.window.CreativeGrabParam;
import com.github.steveice10.mc.protocol.data.game.window.DropItemParam;
import com.github.steveice10.mc.protocol.data.game.window.FillStackParam;
import com.github.steveice10.mc.protocol.data.game.window.MoveToHotbarParam;
import com.github.steveice10.mc.protocol.data.game.window.ShiftClickItemParam;
import com.github.steveice10.mc.protocol.data.game.window.SpreadItemParam;
import com.github.steveice10.mc.protocol.data.game.window.WindowAction;
import com.github.steveice10.mc.protocol.data.game.window.WindowActionParam;
import com.github.steveice10.mc.protocol.data.game.window.WindowType;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerChangeHeldItemPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerChangeHeldItemPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.PlayerInventoryLayout;
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
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Containers and the held item of the MCProtocolLib releases from 1.16.5 until 1.17.1, which open containers alike,
 * with a title the library leaves as the server's JSON text, and derive a click's action from its type and button.
 * Their hotbar actions cover the nine hotbar slots only, so a click cannot swap a slot with the off hand. Each
 * release's {@code InventoryPackets} reads the content and slot packets and builds the click packet, which gained the
 * container's state ID in Minecraft 1.17.
 *
 * <p>The library names menu types as the game's registry does, so their keys are the lowercase constant names in the
 * {@code minecraft} namespace; only the cartography table's menu has a shorter constant.</p>
 */
public abstract class InventoryTranslation implements PacketTranslation<Packet> {
	/**
	 * The container whose slot packet addresses a slot of the player's inventory by its inventory index.
	 */
	protected static final int INVENTORY_INDEX_CONTAINER = -2;
	/**
	 * Every fourth drag button is no button: 0 to 2 drag with the left button, 4 to 6 with the right and 8 to 10 with
	 * the middle one.
	 */
	private static final int DRAG_BUTTONS = 4;
	private static final String CARTOGRAPHY_TABLE = "cartography_table";
	private static final DropItemParam[] DROPS = {DropItemParam.DROP_FROM_SELECTED, DropItemParam.DROP_SELECTED_STACK};

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
		if (packet instanceof ServerOpenWindowPacket open)
			return new ClientboundOpenScreen(open.getWindowId(), menuType(open.getType()),
					ValueTranslator.plain(DefaultComponentSerializer.get().deserialize(open.getName())));
		if (packet instanceof ServerPlayerChangeHeldItemPacket held) return new ClientboundSetHeldSlot(held.getSlot());

		return decodeContents(packet);
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundSetCarriedItem carried)
			return new ClientPlayerChangeHeldItemPacket(carried.getSlot());
		if (packet instanceof ServerboundContainerClick click) return click(click, action(click), param(click));

		return null;
	}

	/**
	 * Reads a packet that fills a container or changes one slot: of a container, of the cursor or of the player's own
	 * inventory.
	 *
	 * @param packet a packet the release received
	 * @return the protocol's packet, or {@code null} when the packet changes no container
	 */
	protected abstract @Nullable ClientboundPacket decodeContents(@NotNull Packet packet);

	/**
	 * Builds the release's click packet, which predicts no changed slots and no picked up stack.
	 *
	 * @param click the click to send
	 * @param action the library's action of the click
	 * @param param the library's parameter of the action
	 * @return the release's click packet
	 */
	protected abstract @NotNull Packet click(
			@NotNull ServerboundContainerClick click,
			@NotNull WindowAction action,
			@NotNull WindowActionParam param
	);

	private static String menuType(WindowType type) {
		String path = type == WindowType.CARTOGRAPHY ? CARTOGRAPHY_TABLE : type.name().toLowerCase(Locale.ROOT);
		return Key.key(Key.MINECRAFT_NAMESPACE, path).asString();
	}

	private static WindowAction action(ServerboundContainerClick click) {
		return switch (click.getClickType()) {
			case PICKUP -> WindowAction.CLICK_ITEM;
			case QUICK_MOVE -> WindowAction.SHIFT_CLICK_ITEM;
			case SWAP -> WindowAction.MOVE_TO_HOTBAR_SLOT;
			case CLONE -> WindowAction.CREATIVE_GRAB_MAX_STACK;
			case THROW -> WindowAction.DROP_ITEM;
			case QUICK_CRAFT -> WindowAction.SPREAD_ITEM;
			case PICKUP_ALL -> WindowAction.FILL_STACK;
		};
	}

	private static WindowActionParam param(ServerboundContainerClick click) {
		int button = click.getButton();
		return switch (click.getClickType()) {
			case PICKUP -> constant(ClickItemParam.values(), button, click);
			case QUICK_MOVE -> constant(ShiftClickItemParam.values(), button, click);
			case SWAP -> hotbar(click);
			case CLONE -> CreativeGrabParam.GRAB;
			case THROW -> constant(DROPS, button, click);
			case QUICK_CRAFT -> button % DRAG_BUTTONS == DRAG_BUTTONS - 1
					? invalid(click)
					: constant(SpreadItemParam.values(), button - button / DRAG_BUTTONS, click);
			case PICKUP_ALL -> FillStackParam.FILL;
		};
	}

	private static MoveToHotbarParam hotbar(ServerboundContainerClick click) {
		if (click.getButton() == PlayerInventoryLayout.OFF_HAND)
			throw new ProtocolUnavailableException("MCProtocolLib releases before 1.18 cannot swap a slot with the "
					+ "off hand");

		return constant(MoveToHotbarParam.values(), click.getButton(), click);
	}

	private static <P extends WindowActionParam> P constant(P[] params, int index, ServerboundContainerClick click) {
		if (index < 0 || index >= params.length) return invalid(click);

		return params[index];
	}

	private static <P> P invalid(ServerboundContainerClick click) {
		throw new IllegalArgumentException("Invalid button " + click.getButton() + " for a " + click.getClickType()
				+ " click");
	}
}
