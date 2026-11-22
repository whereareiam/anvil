package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack;
import me.whereareiam.anvil.player.api.model.text.Text;
import me.whereareiam.anvil.player.api.model.text.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComponentItemTranslatorTest {
	private final ComponentItemTranslator items = new ComponentItemTranslator(new TextTranslator());

	@Test
	void readsTheCustomNameItemNameAndLoreComponents() {
		DataComponents components = new DataComponents(new HashMap<>());
		components.put(DataComponentType.CUSTOM_NAME, Component.text("Buy", NamedTextColor.GREEN));
		components.put(DataComponentType.ITEM_NAME, Component.text("Emerald Token"));
		components.put(DataComponentType.LORE, List.of(Component.text("Costs 64 coins"), Component.text("Click to buy")));

		ItemStack item = items.item(new org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack(791, 3, components));

		assertEquals("Buy", item.getCustomName().plain());
		assertEquals(TextColor.GREEN, item.getCustomName().getStyle().getColor());
		assertEquals("Emerald Token", item.getItemName().plain());
		assertEquals(List.of("Costs 64 coins", "Click to buy"), item.getLore().stream().map(Text::plain).toList());
		assertEquals(791, item.getId());
		assertEquals(3, item.getCount());
	}

	@Test
	void readsAStackWithoutComponentsAsUnnamed() {
		assertEquals(ItemStack.of(791, 3), items.item(new org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack(791, 3)));
		assertEquals(ItemStack.of(791, 3), items.item(new org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack(791, 3,
				new DataComponents(new HashMap<>()))));
	}

	@Test
	void readsNoStackForAnEmptySlot() {
		assertNull(items.item(null));
		assertNull(items.item(new org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack(791, 0)));
	}
}
