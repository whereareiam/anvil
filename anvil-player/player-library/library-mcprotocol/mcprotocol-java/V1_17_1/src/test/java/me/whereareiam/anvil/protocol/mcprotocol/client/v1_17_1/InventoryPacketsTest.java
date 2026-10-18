package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.window.ClickItemParam;
import com.github.steveice10.mc.protocol.data.game.window.WindowAction;
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientWindowActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.type.ClickType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class InventoryPacketsTest {
	private final InventoryPackets packets = new InventoryPackets();

	@Test
	void reportsContainersWithTheirStateIdsAndCarriedStack() {
		assertEquals(new ClientboundContainerSetContent(3, 9, Arrays.asList(
						new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(1, 2), null),
						new me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack(5, 1)),
				packets.decode(new ServerWindowItemsPacket(3, 9, new ItemStack[]{new ItemStack(1, 2), null},
						new ItemStack(5, 1))));
		assertEquals(new ClientboundContainerSetSlot(3, 10, 4, null),
				packets.decode(new ServerSetSlotPacket(3, 10, 4, null)));
		assertEquals(new ClientboundSetPlayerInventory(36, null),
				packets.decode(new ServerSetSlotPacket(254, 0, 36, null)));
	}

	@Test
	void clicksWithTheContainersStateIdAndNoPredictedSlots() {
		ClientWindowActionPacket click = assertInstanceOf(ClientWindowActionPacket.class,
				packets.encode(ServerboundContainerClick.builder()
						.containerId(3)
						.stateId(9)
						.slot(4)
						.button(ClickType.SECONDARY_BUTTON)
						.clickType(ClickType.PICKUP)
						.build()));

		assertEquals(3, click.getWindowId());
		assertEquals(9, click.getStateId());
		assertEquals(4, click.getSlot());
		assertEquals(WindowAction.CLICK_ITEM, click.getAction());
		assertEquals(ClickItemParam.RIGHT_CLICK, click.getParam());
		assertNull(click.getCarriedItem());
		assertEquals(Map.of(), click.getChangedSlots());
	}
}
