package me.whereareiam.anvil.edition.java.protocol.model.world.chunk;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import org.jetbrains.annotations.NotNull;

/**
 * One block the server changes for the player.
 */
@Value
public class BlockChange {
	/**
	 * The block's position in the world.
	 */
	@NotNull BlockPosition position;
	/**
	 * The block's new state, as its global ID in the release's block state registry.
	 */
	int blockState;
}
