package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.inventory.channel.InventoryChannel;
import me.whereareiam.anvil.capability.inventory.model.InventoryAction;
import me.whereareiam.anvil.capability.inventory.model.SlotSelection;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryProviderTest {
	@Test
	void describesTheInventoryCapabilityForTheLibrariesWithAWorkerSide() {
		InventoryProvider provider = new InventoryProvider();

		assertEquals("me.whereareiam.anvil.inventory", provider.descriptor().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Inventory.class, provider.capability());
	}

	@Test
	void requestsThroughThePlayersChannelOnlyOnceTheInventoryIsUsed() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(InventoryChannel.ID));
		channel.answer(InventoryChannel.SELECT, selection -> null);
		channel.answer(InventoryChannel.CLICK, action -> null);
		Inventory inventory = new InventoryProvider().create(new TestProtocolPlayerCapabilityContext(channel));
		assertTrue(channel.requests().isEmpty());
		assertEquals(List.of(InventoryChannel.CHANGED.id()), channel.subscriptions());

		inventory.selectSlot(0);
		inventory.click(4, InventoryClick.LEFT, 0);

		assertEquals(List.of(InventoryChannel.SELECT.id(), InventoryChannel.CLICK.id()), channel.requests());
		assertEquals(List.of(new SlotSelection(0), new InventoryAction(4, InventoryClick.LEFT, 0)), channel.payloads());
	}
}
