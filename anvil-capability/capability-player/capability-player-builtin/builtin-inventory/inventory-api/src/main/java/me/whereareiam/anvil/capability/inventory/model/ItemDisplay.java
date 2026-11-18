package me.whereareiam.anvil.capability.inventory.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.capability.inventory.type.ItemNameSource;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The text a player reads on an item: its name and the lore below it.
 *
 * <p>The client names a stack by its custom name, else by its item name, else by the game's translation of its type,
 * which a simulated player does not have; an item named only by its type has no {@link #getName() name} here and is
 * found by its {@link ItemType}.</p>
 */
@Value
@Builder(toBuilder = true)
public class ItemDisplay {
	/**
	 * An item that shows nothing of its own.
	 */
	public static final ItemDisplay NONE = ItemDisplay.builder().build();

	/**
	 * The name the player reads, as the server sent it, or {@code null} when the client shows the name of the item's
	 * type.
	 */
	@Nullable Text name;
	/**
	 * Where the name comes from, or {@code null} when the item has no name of its own.
	 */
	@Nullable ItemNameSource nameSource;
	/**
	 * The lines of lore below the name, in order.
	 */
	@Singular("lore")
	@NotNull List<Text> lore;
}
