package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.window.ClickItemParam;
import com.github.steveice10.mc.protocol.data.game.window.MoveToHotbarParam;
import com.github.steveice10.mc.protocol.data.game.window.WindowAction;
import com.github.steveice10.mc.protocol.data.game.window.WindowType;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerChangeHeldItemPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientWindowActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerChangeHeldItemPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.java.api.PlayerInventoryLayout;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerClose;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerSetContent;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundContainerSetSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ClientboundOpenScreen;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.container.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.player.ClientboundSetHeldSlot;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.player.ClientboundSetPlayerInventory;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.player.ServerboundSetCarriedItem;
import me.whereareiam.anvil.protocol.java.api.type.interaction.ClickType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryPacketsTest {
	private final InventoryPackets packets = new InventoryPackets(new TextTranslator(), new ItemTranslator(new TextTranslator()));

	@Test
	void readsTheHeldSlotTheServerSelects() {
		assertEquals(new ClientboundSetHeldSlot(6), packets.decode(new ServerPlayerChangeHeldItemPacket(6)));
	}

	@Test
	void opensAScreenWithItsMenuKeyAndTheServersTitle() {
		ClientboundOpenScreen open = assertInstanceOf(ClientboundOpenScreen.class, packets.decode(
				new ServerOpenWindowPacket(3, WindowType.GENERIC_9X3, "{\"text\":\"Anvil \",\"extra\":[{\"text\":\"Menu\"}]}")));

		assertEquals(3, open.getContainerId());
		assertEquals("minecraft:generic_9x3", open.getMenuType());
		assertEquals("Anvil Menu", open.getTitle().plain());
		assertEquals(List.of(Text.literal("Menu")), open.getTitle().getChildren());
		assertEquals("minecraft:cartography_table", assertInstanceOf(ClientboundOpenScreen.class,
				packets.decode(new ServerOpenWindowPacket(4, WindowType.CARTOGRAPHY, "\"Map\""))).getMenuType());
	}

	@Test
	void readsTheContainerTheServerCloses() {
		assertEquals(new ClientboundContainerClose(3), packets.decode(new ServerCloseWindowPacket(3)));
	}

	@Test
	void reportsContainersAtStateZeroWithoutACarriedStack() {
		assertEquals(new ClientboundContainerSetContent(3, 0, Arrays.asList(
						me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack.of(1, 2), null), null),
				packets.decode(new ServerWindowItemsPacket(3, new ItemStack[]{new ItemStack(1, 2), null})));
		assertEquals(new ClientboundContainerSetSlot(3, 0, 4,
						me.whereareiam.anvil.protocol.java.api.model.inventory.ItemStack.of(5, 1)),
				packets.decode(new ServerSetSlotPacket(3, 4, new ItemStack(5, 1))));
	}

	@Test
	void reportsTheInventoryIndexContainerAsThePlayersInventory() {
		assertEquals(new ClientboundSetPlayerInventory(36, null),
				packets.decode(new ServerSetSlotPacket(254, 36, null)));
		assertEquals(new ClientboundContainerSetSlot(-1, 0, -1, null),
				packets.decode(new ServerSetSlotPacket(255, -1, null)));
	}

	@Test
	void clicksWithAnActionNumberOfItsOwnAndNoPredictedStack() {
		ServerboundContainerClick click = ServerboundContainerClick.builder()
				.containerId(3)
				.stateId(9)
				.slot(4)
				.button(ClickType.PRIMARY_BUTTON)
				.clickType(ClickType.PICKUP)
				.build();

		ClientWindowActionPacket first = assertInstanceOf(ClientWindowActionPacket.class, packets.encode(click));
		ClientWindowActionPacket second = assertInstanceOf(ClientWindowActionPacket.class, packets.encode(click));

		assertEquals(3, first.getWindowId());
		assertEquals(4, first.getSlot());
		assertEquals(WindowAction.CLICK_ITEM, first.getAction());
		assertEquals(ClickItemParam.LEFT_CLICK, first.getParam());
		assertNull(first.getClickedItem());
		assertEquals(first.getActionId() + 1, second.getActionId());
	}

	@Test
	void swapsWithAHotbarSlotButNotWithTheOffHand() {
		ServerboundContainerClick.ServerboundContainerClickBuilder swap = ServerboundContainerClick.builder()
				.containerId(0)
				.slot(10)
				.clickType(ClickType.SWAP);

		assertEquals(MoveToHotbarParam.SLOT_3, assertInstanceOf(ClientWindowActionPacket.class,
				packets.encode(swap.button(2).build())).getParam());
		assertThrows(ProtocolUnavailableException.class,
				() -> packets.encode(swap.button(PlayerInventoryLayout.OFF_HAND).build()));
	}

	@Test
	void selectsTheHeldItem() {
		assertEquals(5, assertInstanceOf(ClientPlayerChangeHeldItemPacket.class,
				packets.encode(new ServerboundSetCarriedItem(5))).getSlot());
	}
}
