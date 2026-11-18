package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * A container the server opened for the player, such as a menu, as an {@link InventoryWatch} found it. It follows the
 * container while it stays open; once the server opens another or closes it, its locator refuses to act, so a test
 * never clicks into a screen it did not mean.
 *
 * <pre>{@code
 * Screen shop = watch.screen("Shop");
 * shop.items().named("Swords").click(InventoryClick.LEFT);
 * }</pre>
 */
public interface Screen {
	/**
	 * Returns the screen's title as the server sent it.
	 *
	 * @return the title
	 */
	@NotNull Text title();

	/**
	 * Returns the vanilla ID of the container's menu type.
	 *
	 * @return the menu type, such as {@code minecraft:generic_9x3}
	 */
	@NotNull String menuType();

	/**
	 * Returns the screen's latest state, without waiting; once the screen closed, its last state.
	 *
	 * @return the latest snapshot of this screen
	 */
	@NotNull InventorySnapshot snapshot();

	/**
	 * Tells whether the screen is still the one the player sees.
	 *
	 * @return whether no other screen replaced it
	 */
	boolean open();

	/**
	 * Returns a locator of every item of this screen, which acts only while the screen is open.
	 *
	 * @return a locator without conditions
	 */
	@NotNull ItemLocator items();
}
