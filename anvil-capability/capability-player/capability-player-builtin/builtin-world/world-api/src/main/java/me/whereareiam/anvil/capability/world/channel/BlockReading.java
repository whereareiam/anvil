package me.whereareiam.anvil.capability.world.channel;

import lombok.Value;
import me.whereareiam.anvil.capability.world.model.block.BlockIdentity;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One block as the player's worker read it from the world its client knows, which the host turns into a
 * {@link me.whereareiam.anvil.capability.world.model.block.Block Block}.
 */
@Value
public class BlockReading {
	/**
	 * Where the block is.
	 */
	@NotNull BlockPosition position;
	/**
	 * The global ID of the block's state.
	 */
	int state;
	/**
	 * What the state is by name, or {@code null} when Anvil has no block data for the protocol.
	 */
	@Nullable BlockIdentity identity;
	/**
	 * The protocol the state ID belongs to.
	 */
	int protocol;
}
