package me.whereareiam.anvil.capability.inventory.model;

import lombok.Value;
import me.whereareiam.anvil.capability.inventory.type.InventoryClick;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable inventory operation request shared by host and native bindings.
 */
@Value
public class InventoryAction {
	int slot;
	@NotNull InventoryClick click;
	int button;
}
