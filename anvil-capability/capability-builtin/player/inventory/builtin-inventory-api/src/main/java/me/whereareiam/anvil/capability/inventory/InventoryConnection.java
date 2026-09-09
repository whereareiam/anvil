package me.whereareiam.anvil.capability.inventory;

import me.whereareiam.anvil.capability.inventory.model.InventorySnapshot;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Player-scoped inventory actions and observations supplied by an outer connection adapter.
 * Observers are released with the owning player.
 */
public interface InventoryConnection {
	/**
	 * Selects a hotbar slot.
	 * @param slot hotbar slot from zero through eight
	 */
	void select(int slot);

	/**
	 * Applies an inventory action.
	 * @param slot target inventory slot
	 * @param click action to apply
	 * @param button hotbar key index for a hotbar swap; otherwise action-specific
	 */
	void click(int slot, @NotNull InventoryClick click, int button);

	/**
	 * Registers complete inventory observations.
	 * @param observer receives subsequent inventory snapshots
	 */
	void observe(@NotNull Consumer<InventorySnapshot> observer);

	/**
	 * Waits for a local observation while preserving native connection diagnostics.
	 * @param condition observation predicate
	 * @param description diagnostic action description
	 * @param timeout positive wait limit
	 */
	void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout);
}
