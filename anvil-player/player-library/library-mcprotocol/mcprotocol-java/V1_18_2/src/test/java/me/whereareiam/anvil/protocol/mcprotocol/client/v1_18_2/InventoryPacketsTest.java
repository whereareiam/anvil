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
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.java.api.type.ClickType;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryPacketsTest {
	private final InventoryPackets packets = new InventoryPackets();

	@Test
	void decodesAnOpenedContainerWithItsMenuTypeAndPlainTitle() {
		ClientboundOpenScreenPacket chest = new ClientboundOpenScreenPacket(2, ContainerType.GENERIC_9X1,
				Component.text("Chest"));
		ClientboundOpenScreenPacket map = new ClientboundOpenScreenPacket(3, ContainerType.CARTOGRAPHY,
				Component.text("Map"));

		assertEquals(new ClientboundOpenScreen(2, "minecraft:generic_9x1", "Chest"), packets.decode(chest));
		assertEquals("minecraft:cartography_table",
				assertInstanceOf(ClientboundOpenScreen.class, packets.decode(map)).getMenuType());
	}

	@Test
	void decodesContainerContentsWithAnEmptySlotForEachMissingStack() {
		ClientboundContainerSetContentPacket content = new ClientboundContainerSetContentPacket(2, 7,
				stacks(null, stack(800, 3)), stack(5, 1));

		assertEquals(new ClientboundContainerSetContent(2, 7, Arrays.asList(null, new ItemStack(800, 3)),
				new ItemStack(5, 1)), packets.decode(content));
	}

	@Test
	void decodesSlotsOfAContainerAndOfTheCursor() {
		assertEquals(new ClientboundContainerSetSlot(2, 8, 0, new ItemStack(1, 64)),
				packets.decode(new ClientboundContainerSetSlotPacket(2, 8, 0, stack(1, 64))));
		// The library decodes this container ID as an unsigned byte, so vanilla's cursor -1 and inventory index -2
		// arrive as 255 and 254.
		assertEquals(new ClientboundContainerSetSlot(-1, 8, -1, null),
				packets.decode(new ClientboundContainerSetSlotPacket(255, 8, -1, null)));
		assertEquals(new ClientboundContainerSetSlot(-2, 0, 40, new ItemStack(5, 1)),
				packets.decode(new ClientboundContainerSetSlotPacket(254, 0, 40, stack(5, 1))));
	}

	@Test
	void encodesTheHeldHotbarSlot() {
		ServerboundSetCarriedItemPacket packet = assertInstanceOf(ServerboundSetCarriedItemPacket.class,
				packets.encode(new ServerboundSetCarriedItem(3)));

		assertEquals(3, packet.getSlot());
	}

	@Test
	void encodesAClickWithTheContainerStateAndWithoutPredictedItems() {
		ServerboundContainerClickPacket packet = assertInstanceOf(ServerboundContainerClickPacket.class,
				packets.encode(click(ClickType.PICKUP, 0)));

		assertEquals(2, packet.getContainerId());
		assertEquals(7, packet.getStateId());
		assertEquals(4, packet.getSlot());
		assertEquals(ContainerActionType.CLICK_ITEM, packet.getAction());
		assertEquals(ClickItemAction.LEFT_CLICK, packet.getParam());
		assertNull(packet.getCarriedItem());
		assertTrue(packet.getChangedSlots().isEmpty());
	}

	@Test
	void encodesEachClickTypeAndButtonAsItsContainerAction() {
		assertAction(ClickType.PICKUP, 0, ContainerActionType.CLICK_ITEM, ClickItemAction.LEFT_CLICK);
		assertAction(ClickType.PICKUP, 1, ContainerActionType.CLICK_ITEM, ClickItemAction.RIGHT_CLICK);
		assertAction(ClickType.QUICK_MOVE, 0, ContainerActionType.SHIFT_CLICK_ITEM, ShiftClickItemAction.LEFT_CLICK);
		assertAction(ClickType.QUICK_MOVE, 1, ContainerActionType.SHIFT_CLICK_ITEM, ShiftClickItemAction.RIGHT_CLICK);
		assertAction(ClickType.SWAP, 0, ContainerActionType.MOVE_TO_HOTBAR_SLOT, MoveToHotbarAction.SLOT_1);
		assertAction(ClickType.SWAP, 8, ContainerActionType.MOVE_TO_HOTBAR_SLOT, MoveToHotbarAction.SLOT_9);
		assertAction(ClickType.SWAP, 40, ContainerActionType.MOVE_TO_HOTBAR_SLOT, MoveToHotbarAction.OFF_HAND);
		assertAction(ClickType.CLONE, 2, ContainerActionType.CREATIVE_GRAB_MAX_STACK, CreativeGrabAction.GRAB);
		assertAction(ClickType.THROW, 0, ContainerActionType.DROP_ITEM, DropItemAction.DROP_FROM_SELECTED);
		assertAction(ClickType.THROW, 1, ContainerActionType.DROP_ITEM, DropItemAction.DROP_SELECTED_STACK);
		assertAction(ClickType.QUICK_CRAFT, 0, ContainerActionType.SPREAD_ITEM, SpreadItemAction.LEFT_MOUSE_BEGIN_DRAG);
		assertAction(ClickType.QUICK_CRAFT, 5, ContainerActionType.SPREAD_ITEM, SpreadItemAction.RIGHT_MOUSE_ADD_SLOT);
		assertAction(ClickType.QUICK_CRAFT, 10, ContainerActionType.SPREAD_ITEM,
				SpreadItemAction.MIDDLE_MOUSE_END_DRAG);
		assertAction(ClickType.PICKUP_ALL, 0, ContainerActionType.FILL_STACK, FillStackAction.FILL);
	}

	@Test
	void refusesAButtonTheClickTypeDoesNotHave() {
		assertThrows(IllegalArgumentException.class, () -> packets.encode(click(ClickType.PICKUP, 2)));
		assertThrows(IllegalArgumentException.class, () -> packets.encode(click(ClickType.SWAP, 9)));
		assertThrows(IllegalArgumentException.class, () -> packets.encode(click(ClickType.THROW, 2)));
		assertThrows(IllegalArgumentException.class, () -> packets.encode(click(ClickType.QUICK_CRAFT, 3)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryInventoryPacketOfTheRelease() {
		assertEquals(Set.of(
				ClientboundOpenScreen.class,
				ClientboundContainerSetContent.class,
				ClientboundContainerSetSlot.class,
				ServerboundSetCarriedItem.class,
				ServerboundContainerClick.class
		), packets.packets());
	}

	private void assertAction(ClickType type, int button, ContainerActionType action, ContainerAction param) {
		ServerboundContainerClickPacket packet = (ServerboundContainerClickPacket) packets.encode(click(type, button));

		assertEquals(action, packet.getAction(), type + " " + button);
		assertEquals(param, packet.getParam(), type + " " + button);
	}

	private static ServerboundContainerClick click(ClickType type, int button) {
		return ServerboundContainerClick.builder()
				.containerId(2)
				.stateId(7)
				.slot(4)
				.clickType(type)
				.button(button)
				.build();
	}

	private static com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack stack(int id, int count) {
		return new com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack(id, count);
	}

	private static com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack[] stacks(
			com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack... stacks
	) {
		return stacks;
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
