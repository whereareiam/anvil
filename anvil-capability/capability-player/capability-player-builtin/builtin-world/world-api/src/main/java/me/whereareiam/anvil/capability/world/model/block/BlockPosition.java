package me.whereareiam.anvil.capability.world.model.block;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * The position of one block in the player's level, in whole block coordinates: the block that spans x to x + 1, y to
 * y + 1 and z to z + 1.
 *
 * <pre>{@code
 * BlockPosition door = new BlockPosition(12, 64, -3);
 * Optional<Block> block = player.capability(World.class).blocks().block(door);
 * }</pre>
 */
@Value
@Builder
@AllArgsConstructor
public class BlockPosition {
	int x;
	int y;
	int z;
}
