package me.whereareiam.anvil.capability.inventory.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The latest state of the screen a simulated client shows: the container the server opened, such as a chest a plugin
 * uses as a menu, or the player's own inventory when no other container is open.
 *
 * <p>Each container the server opens, and each return to the player's own inventory, is a new screen, which
 * {@link #screen()} numbers in the order the player saw them; every later state of one screen keeps its number. A
 * screen is published once the server filled it, so its contents are never missing.</p>
 */
@Value
@Builder(toBuilder = true)
public class InventorySnapshot {
	/**
	 * ID of the shown container, {@code 0} for the player's own inventory.
	 */
	int containerId;
	/**
	 * The container's state ID, which the player's next click carries.
	 */
	int stateId;
	/**
	 * Number of the screen this state belongs to, counting from {@code 0} for the player's inventory the player starts
	 * with.
	 */
	int screen;
	/**
	 * Vanilla ID of the container's menu type, such as {@code minecraft:generic_9x3}, or {@code null} for the player's
	 * own inventory.
	 */
	@Nullable String menuType;
	/**
	 * The container's title as the server sent it, or {@code null} for the player's own inventory.
	 */
	@Nullable Text title;
	/**
	 * The items in the container's slots, by slot; empty slots are left out.
	 */
	@NotNull @Singular List<InventoryItem> items;

	/**
	 * Tells whether the screen is the player's own inventory rather than a container the server opened.
	 *
	 * @return whether no other container is open
	 */
	public boolean playerInventory() {
		return containerId == 0;
	}
}
