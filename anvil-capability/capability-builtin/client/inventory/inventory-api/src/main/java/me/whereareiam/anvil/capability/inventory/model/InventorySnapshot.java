package me.whereareiam.anvil.capability.inventory.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Latest inventory or container state received by a simulated client.
 */
@Value
@Builder(toBuilder = true)
public class InventorySnapshot {
	int containerId;
	int stateId;
	@NotNull @Singular List<InventoryItem> items;
}
