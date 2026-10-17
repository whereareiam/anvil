package me.whereareiam.anvil.capability.inventory.model;

import lombok.Value;

/**
 * Immutable inventory operation request shared by host and native bindings.
 */
@Value
public class SlotSelection {
	int slot;
}
