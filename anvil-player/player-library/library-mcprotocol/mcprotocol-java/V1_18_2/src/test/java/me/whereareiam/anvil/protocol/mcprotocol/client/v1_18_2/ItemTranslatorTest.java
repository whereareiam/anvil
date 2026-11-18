package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack;
import me.whereareiam.anvil.player.api.model.text.Text;
import me.whereareiam.anvil.player.api.model.text.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemTranslatorTest {
	private final ItemTranslator items = new ItemTranslator(new TextTranslator());

	@Test
	void readsTheCustomNameAndLoreOfTheStacksDisplay() {
		ItemStack item = items.item(stack(display(
				new StringTag("Name", "{\"text\":\"Buy\",\"color\":\"green\"}"),
				lore("{\"text\":\"Costs 64 coins\"}", "\"Click to buy\"")
		)));

		assertEquals("Buy", item.getCustomName().plain());
		assertEquals(TextColor.GREEN, item.getCustomName().getStyle().getColor());
		assertNull(item.getItemName());
		assertEquals(List.of("Costs 64 coins", "Click to buy"), item.getLore().stream().map(Text::plain).toList());
		assertEquals(791, item.getId());
		assertEquals(3, item.getCount());
	}

	@Test
	void readsAStackWithoutDisplayAsUnnamed() {
		assertEquals(ItemStack.of(791, 3), items.item(new com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack(
				791, 3, new CompoundTag(""))));
		assertEquals(ItemStack.of(791, 3), items.item(new com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack(
				791, 3)));
	}

	@Test
	void leavesANameThatIsNoJsonTextOutAsTheVanillaClientDoes() {
		ItemStack item = items.item(stack(display(new StringTag("Name", "{broken"), lore("\"Kept\""))));

		assertNull(item.getCustomName());
		assertEquals(List.of("Kept"), item.getLore().stream().map(Text::plain).toList());
	}

	@Test
	void leavesTheLoreOutWhenALineIsNoJsonText() {
		ItemStack item = items.item(stack(display(lore("\"First\"", "{broken"))));

		assertEquals(List.of(), item.getLore());
	}

	private static com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack stack(CompoundTag display) {
		CompoundTag nbt = new CompoundTag("");
		nbt.put(display);
		return new com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack(791, 3, nbt);
	}

	private static CompoundTag display(com.github.steveice10.opennbt.tag.builtin.Tag... tags) {
		CompoundTag display = new CompoundTag("display");
		for (var tag : tags) display.put(tag);
		return display;
	}

	private static ListTag lore(String... lines) {
		ListTag lore = new ListTag("Lore");
		for (String line : lines) lore.add(new StringTag("", line));
		return lore;
	}
}
