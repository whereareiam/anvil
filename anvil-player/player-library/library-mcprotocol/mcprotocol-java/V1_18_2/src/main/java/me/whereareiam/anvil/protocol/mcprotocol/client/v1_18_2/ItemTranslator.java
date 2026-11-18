package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.DefaultComponentSerializer;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.google.gson.JsonParseException;
import me.whereareiam.anvil.edition.java.protocol.model.inventory.ItemStack;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Translates the item stacks of the MCProtocolLib releases from 1.18.2 until 1.19.4, which keep a stack's custom name and
 * lore in its NBT: the {@code display} compound's {@code Name} and each line of its {@code Lore} are JSON text, which
 * the release's text translator turns into the protocol's text. These releases have no item names.
 *
 * <p>The client reads the text as the vanilla client does: a name that is no valid JSON text leaves the stack
 * unnamed, and a line of lore that is none leaves it without lore.</p>
 */
public final class ItemTranslator {
	private static final String DISPLAY = "display";
	private static final String NAME = "Name";
	private static final String LORE = "Lore";

	private final ComponentTranslator text;

	/**
	 * Creates the item translation of a release.
	 *
	 * @param text translates the release's text components
	 */
	public ItemTranslator(@NotNull ComponentTranslator text) {
		this.text = text;
	}

	/**
	 * Translates the library's item stack.
	 *
	 * @param stack the library's stack, or {@code null} for an empty slot
	 * @return the protocol's stack, or {@code null} for an empty slot or a stack of no items
	 */
	public @Nullable ItemStack item(
			com.github.steveice10.mc.protocol.data.game.entity.metadata.@Nullable ItemStack stack
	) {
		if (stack == null || stack.getAmount() <= 0) return null;

		ItemStack.ItemStackBuilder item = ItemStack.builder().id(stack.getId()).count(stack.getAmount());
		CompoundTag nbt = stack.getNbt();
		if (nbt == null || !(nbt.get(DISPLAY) instanceof CompoundTag display)) return item.build();

		if (display.get(NAME) instanceof StringTag name) item.customName(text(name));
		if (display.get(LORE) instanceof ListTag lore) item.lore(lore(lore));
		return item.build();
	}

	/**
	 * Translates the library's item stacks, keeping their order and their empty slots.
	 *
	 * @param stacks the library's stacks
	 * @return the protocol's stacks, {@code null} for each empty slot
	 */
	public @NotNull List<@Nullable ItemStack> items(
			com.github.steveice10.mc.protocol.data.game.entity.metadata.@Nullable ItemStack @NotNull [] stacks
	) {
		List<ItemStack> items = new ArrayList<>(stacks.length);
		for (var stack : stacks) items.add(item(stack));
		return Collections.unmodifiableList(items);
	}

	private List<Text> lore(ListTag lines) {
		List<Text> lore = new ArrayList<>(lines.size());
		for (Tag line : lines) {
			Text read = line instanceof StringTag json ? text(json) : null;
			if (read == null) return List.of();
			lore.add(read);
		}

		return lore;
	}

	private @Nullable Text text(StringTag json) {
		try {
			return text.translate(DefaultComponentSerializer.get().deserialize(json.getValue()));
		} catch (JsonParseException exception) {
			return null;
		}
	}
}
