package me.whereareiam.anvil.edition.java.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

/**
 * How the protocol numbers the slots of a player's own inventory. Packets about the inventory itself, such as
 * {@code ClientboundSetPlayerInventory}, and the hotbar swap of a container click address a slot by its inventory
 * index: the hotbar, the main inventory, the armor from feet to head, then the off hand. The player's inventory
 * container, which the client shows and clicks address, lists the same slots in another order: the crafting result
 * and grid, the armor from head to feet, the main inventory, the hotbar, then the off hand.
 */
public final class PlayerInventoryLayout {
	/**
	 * ID of the player's inventory container, which the player has open whenever no other container is.
	 */
	public static final int CONTAINER_ID = 0;

	/**
	 * Inventory index of the first hotbar slot; the hotbar's slots follow it.
	 */
	public static final int HOTBAR = 0;
	/**
	 * Number of hotbar slots, which hotbar selections and swaps count from {@link #HOTBAR}.
	 */
	public static final int HOTBAR_SIZE = 9;
	/**
	 * Inventory index of the first slot of the main inventory, which follows the hotbar.
	 */
	public static final int MAIN = HOTBAR + HOTBAR_SIZE;
	/**
	 * Number of slots of the main inventory.
	 */
	public static final int MAIN_SIZE = 27;
	/**
	 * Inventory index of the feet's armor slot, followed by the legs', the chest's and the head's.
	 */
	public static final int ARMOR = MAIN + MAIN_SIZE;
	/**
	 * Number of armor slots.
	 */
	public static final int ARMOR_SIZE = 4;
	/**
	 * Inventory index of the off hand, which a hotbar swap also names as its button.
	 */
	public static final int OFF_HAND = ARMOR + ARMOR_SIZE;

	/**
	 * Container slot of the head's armor, the first of the armor slots the container lists from head to feet.
	 */
	private static final int CONTAINER_HEAD = 5;
	/**
	 * Container slot of the first hotbar slot, which follows the main inventory in the container.
	 */
	private static final int CONTAINER_HOTBAR = MAIN + MAIN_SIZE;
	/**
	 * Container slot of the off hand, the container's last slot.
	 */
	private static final int CONTAINER_OFF_HAND = CONTAINER_HOTBAR + HOTBAR_SIZE;

	private PlayerInventoryLayout() {
	}

	/**
	 * Tells whether an inventory index is a hotbar slot.
	 *
	 * @param index inventory index
	 * @return whether the index lies in the hotbar
	 */
	public static boolean hotbar(int index) {
		return index >= HOTBAR && index < HOTBAR + HOTBAR_SIZE;
	}

	/**
	 * Maps an inventory index to its slot in the player's inventory container. The main inventory keeps its numbers,
	 * the hotbar follows it, the armor is listed from head to feet and the off hand comes last.
	 *
	 * @param index inventory index
	 * @return the container slot, or empty for an index the inventory does not have
	 */
	public static @NotNull OptionalInt containerSlot(int index) {
		if (hotbar(index)) return OptionalInt.of(CONTAINER_HOTBAR + index - HOTBAR);
		if (index >= MAIN && index < ARMOR) return OptionalInt.of(index);
		if (index >= ARMOR && index < OFF_HAND) return OptionalInt.of(CONTAINER_HEAD + ARMOR + ARMOR_SIZE - 1 - index);
		if (index == OFF_HAND) return OptionalInt.of(CONTAINER_OFF_HAND);

		return OptionalInt.empty();
	}

	/**
	 * Maps a slot of the player's inventory container to its inventory index, the inverse of
	 * {@link #containerSlot(int)}. The container's crafting slots hold no slot of the inventory.
	 *
	 * @param slot container slot
	 * @return the inventory index, or empty for a slot that holds no slot of the inventory
	 */
	public static @NotNull OptionalInt inventoryIndex(int slot) {
		if (slot >= CONTAINER_HOTBAR && slot < CONTAINER_HOTBAR + HOTBAR_SIZE)
			return OptionalInt.of(HOTBAR + slot - CONTAINER_HOTBAR);
		if (slot >= MAIN && slot < ARMOR) return OptionalInt.of(slot);
		if (slot >= CONTAINER_HEAD && slot < CONTAINER_HEAD + ARMOR_SIZE)
			return OptionalInt.of(ARMOR + ARMOR_SIZE - 1 - (slot - CONTAINER_HEAD));
		if (slot == CONTAINER_OFF_HAND) return OptionalInt.of(OFF_HAND);

		return OptionalInt.empty();
	}

	/**
	 * Maps an inventory index to its slot in a container the server opened. Every such container lists the player's
	 * main inventory and then the hotbar as its last slots, below its own; armor and the off hand are not shown.
	 *
	 * @param index inventory index
	 * @param slots number of slots of the container, the player's included
	 * @return the container slot, or empty for an index the container does not show
	 */
	public static @NotNull OptionalInt menuSlot(int index, int slots) {
		int main = slots - MAIN_SIZE - HOTBAR_SIZE;
		if (main < 0) return OptionalInt.empty();
		if (index >= MAIN && index < ARMOR) return OptionalInt.of(main + index - MAIN);
		if (hotbar(index)) return OptionalInt.of(main + MAIN_SIZE + index - HOTBAR);

		return OptionalInt.empty();
	}

	/**
	 * Maps a slot of a container the server opened to the inventory index it shows, the inverse of
	 * {@link #menuSlot(int, int)}.
	 *
	 * @param slot container slot
	 * @param slots number of slots of the container, the player's included
	 * @return the inventory index, or empty for one of the container's own slots
	 */
	public static @NotNull OptionalInt menuInventoryIndex(int slot, int slots) {
		int main = slots - MAIN_SIZE - HOTBAR_SIZE;
		if (main < 0 || slot < main || slot >= slots) return OptionalInt.empty();
		if (slot < main + MAIN_SIZE) return OptionalInt.of(MAIN + slot - main);

		return OptionalInt.of(HOTBAR + slot - main - MAIN_SIZE);
	}
}
