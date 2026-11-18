package me.whereareiam.anvil.edition.java.protocol.model.inventory;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A stack of items in a slot, as inventory packets carry it, with the text the client shows for it. An empty slot
 * carries no stack at all.
 *
 * <p>The client names a stack by its custom name, else by its item name, else by the item's own translation. Before
 * Minecraft 1.20.5 a stack keeps its custom name and lore in its NBT and has no item name; from 1.20.5 on it keeps all
 * three as data components. Either way the protocol carries them as the text the server sent.</p>
 *
 * <pre>{@code
 * ItemStack stack = slot.getItem();
 * String name = stack.getCustomName() == null ? null : stack.getCustomName().plain();
 * List<String> lore = stack.getLore().stream().map(Text::plain).toList();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class ItemStack {
	/**
	 * Numeric ID of the item in the release's item registry.
	 */
	int id;
	/**
	 * Number of items in the stack.
	 */
	int count;
	/**
	 * The name a player or plugin gave the stack, as an anvil does, or {@code null} when it has none.
	 */
	@Nullable Text customName;
	/**
	 * The stack's own name, which replaces the item's translation without counting as renamed, or {@code null} when it
	 * has none; releases before Minecraft 1.20.5 have no item names.
	 */
	@Nullable Text itemName;
	/**
	 * The lines of lore shown below the stack's name, in order.
	 */
	@Singular("lore")
	@NotNull List<Text> lore;

	/**
	 * Creates a stack of an item that has no name or lore of its own.
	 *
	 * @param id numeric ID of the item in the release's item registry
	 * @param count number of items in the stack
	 * @return the stack
	 */
	public static @NotNull ItemStack of(int id, int count) {
		return builder().id(id).count(count).build();
	}
}
