package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.inventory.model.InventoryItem;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds items of a screen by what the player sees of them, such as a menu's buttons, and clicks them without naming
 * slots. A locator is an immutable description: each condition returns a new locator, and every action finds its
 * items again in the screen's latest state, so a locator stays valid while the screen changes.
 *
 * <p>Names and lore are compared by the plain text the player reads, through {@link Text#plain()}; a predicate on
 * {@link Text} can check their colors and styles as well. Like the player, a locator sees only an item's own name: an
 * item the client shows by the game's name of its type is found by {@link #type(String)}.</p>
 *
 * <p>A locator is strict: {@link #item()} and {@link #click(InventoryClick)} wait until exactly one item matches and
 * fail naming every match when several do. {@link #first()} and {@link #nth(int)} pick one of several in slot
 * order.</p>
 *
 * <pre>{@code
 * ItemLocator buy = inventory.items()
 *         .type("minecraft:emerald")
 *         .named("Buy")
 *         .loreContaining("64 coins");
 * buy.click(InventoryClick.LEFT);
 * }</pre>
 */
public interface ItemLocator {
	/**
	 * Narrows the locator to items of one type. Items carry their type when Anvil ships item data for the player's
	 * protocol; where it ships none, every action of a locator with a type fails, saying so.
	 *
	 * @param identifier registry key of the item's type, such as {@code minecraft:emerald}
	 * @return a locator that also requires the type
	 */
	@NotNull ItemLocator type(@NotNull String identifier);

	/**
	 * Narrows the locator to items whose own name reads exactly as given.
	 *
	 * @param name the plain text of the name
	 * @return a locator that also requires the name
	 */
	@NotNull ItemLocator named(@NotNull String name);

	/**
	 * Narrows the locator to items whose own name contains a text.
	 *
	 * @param text a part of the name's plain text
	 * @return a locator that also requires the text in the name
	 */
	@NotNull ItemLocator nameContaining(@NotNull String text);

	/**
	 * Narrows the locator to items whose own name satisfies a condition, such as its color.
	 *
	 * @param description what the condition requires, named in failures
	 * @param name condition on the name as the server sent it
	 * @return a locator that also requires the condition
	 */
	@NotNull ItemLocator named(@NotNull String description, @NotNull Predicate<Text> name);

	/**
	 * Narrows the locator to items with a line of lore that reads exactly as given.
	 *
	 * @param line the plain text of the line
	 * @return a locator that also requires the line
	 */
	@NotNull ItemLocator lore(@NotNull String line);

	/**
	 * Narrows the locator to items with a line of lore that contains a text.
	 *
	 * @param text a part of a line's plain text
	 * @return a locator that also requires the text in the lore
	 */
	@NotNull ItemLocator loreContaining(@NotNull String text);

	/**
	 * Narrows the locator to items with a line of lore that satisfies a condition.
	 *
	 * @param description what the condition requires, named in failures
	 * @param line condition on a line as the server sent it
	 * @return a locator that also requires the condition
	 */
	@NotNull ItemLocator lore(@NotNull String description, @NotNull Predicate<Text> line);

	/**
	 * Narrows the locator to items that satisfy a condition, such as their slot or amount.
	 *
	 * @param description what the condition requires, named in failures
	 * @param item condition on the item
	 * @return a locator that also requires the condition
	 */
	@NotNull ItemLocator matching(@NotNull String description, @NotNull Predicate<InventoryItem> item);

	/**
	 * Picks the first of the matching items, in slot order.
	 *
	 * @return a locator of at most one item
	 */
	default @NotNull ItemLocator first() {
		return nth(0);
	}

	/**
	 * Picks one of the matching items, in slot order.
	 *
	 * @param index zero-based position among the matching items
	 * @return a locator of at most one item
	 * @throws IllegalArgumentException when the index is negative or the locator already picks one
	 */
	@NotNull ItemLocator nth(int index);

	/**
	 * Returns the matching items of the screen's latest state, without waiting.
	 *
	 * @return the items in slot order, empty when none matches
	 */
	@NotNull List<InventoryItem> all();

	/**
	 * Counts the matching items of the screen's latest state, without waiting.
	 *
	 * @return the number of matching items
	 */
	default int count() {
		return all().size();
	}

	/**
	 * Waits, using the default timeout, until exactly one item matches.
	 *
	 * @return the item
	 * @throws IllegalStateException when no item or several items match when the wait ends, or the screen closed
	 */
	default @NotNull InventoryItem item() {
		return item(SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until exactly one item matches.
	 *
	 * @param timeout maximum wait
	 * @return the item
	 * @throws IllegalStateException when no item or several items match when the wait ends, or the screen closed
	 */
	@NotNull InventoryItem item(@NotNull Duration timeout);

	/**
	 * Waits, using the default timeout, until exactly one item matches and clicks its slot, as
	 * {@link Inventory#click(int, InventoryClick, int)} does with button zero.
	 *
	 * @param click click mode, other than {@link InventoryClick#HOTBAR_SWAP}, which takes a button
	 * @throws IllegalStateException when no item or several items match when the wait ends, or the screen closed
	 */
	default void click(@NotNull InventoryClick click) {
		click(click, 0);
	}

	/**
	 * Waits, using the default timeout, until exactly one item matches and clicks its slot, as
	 * {@link Inventory#click(int, InventoryClick, int)} does.
	 *
	 * @param click click mode
	 * @param button hotbar slot {@code 0..8} or the off hand {@code 40} for {@link InventoryClick#HOTBAR_SWAP},
	 *               otherwise zero
	 * @throws IllegalStateException when no item or several items match when the wait ends, or the screen closed
	 */
	void click(@NotNull InventoryClick click, int button);
}
