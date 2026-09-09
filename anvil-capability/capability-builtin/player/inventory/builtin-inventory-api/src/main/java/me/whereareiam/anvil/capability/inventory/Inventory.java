package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Observes and interacts with a simulated player's inventory and open container.
 */
public interface Inventory extends PlayerCapability {
	/**
	 * Returns the latest inventory state immediately without waiting.
	 *
	 * @return latest inventory snapshot
	 */
	@NotNull InventorySnapshot snapshot();

	/**
	 * Waits for matching inventory state using the default timeout.
	 *
	 * @param predicate inventory predicate
	 * @return matching inventory snapshot
	 */
	default @NotNull InventorySnapshot matching(@NotNull Predicate<InventorySnapshot> predicate) {
		return matching(predicate, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for matching inventory state.
	 *
	 * @param predicate inventory predicate
	 * @param timeout maximum wait
	 * @return matching inventory snapshot
	 */
	@NotNull InventorySnapshot matching(
			@NotNull Predicate<InventorySnapshot> predicate,
			@NotNull Duration timeout
	);

	/**
	 * Changes the selected hotbar slot.
	 *
	 * @param slot zero-based slot in the range {@code 0..8}
	 */
	void selectSlot(int slot);

	/**
	 * Clicks an inventory slot using the latest received container state.
	 *
	 * @param slot container slot
	 * @param click click mode
	 * @param button hotbar button for {@link InventoryClick#HOTBAR_SWAP}, otherwise zero
	 */
	void click(int slot, @NotNull InventoryClick click, int button);
}
