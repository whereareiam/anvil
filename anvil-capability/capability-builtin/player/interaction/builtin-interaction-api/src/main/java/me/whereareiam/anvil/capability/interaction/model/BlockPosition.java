package me.whereareiam.anvil.capability.interaction.model;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable integer block coordinate.
 */
@Value
@Builder
public class BlockPosition {
	int x;
	int y;
	int z;
}
