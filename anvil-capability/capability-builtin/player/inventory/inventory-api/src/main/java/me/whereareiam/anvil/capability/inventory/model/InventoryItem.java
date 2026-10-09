package me.whereareiam.anvil.capability.inventory.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * One item entry in a client-side inventory snapshot.
 */
@Value
@Builder
public class InventoryItem {
	int slot;
	int protocolId;
	int amount;
	@Nullable String identifier;
}
