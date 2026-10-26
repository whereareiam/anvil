package me.whereareiam.anvil.capability.world;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.world.model.BlockIdentity;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import me.whereareiam.anvil.protocol.java.data.BlockRegistry;
import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Names the block states of one player's release through the block data Anvil ships for it, or leaves every block
 * known by its state ID only when there is none for the release.
 */
@RequiredArgsConstructor
final class BlockNames {
	private final @Nullable BlockRegistry registry;

	/**
	 * Tells whether the release's block data names the states.
	 *
	 * @return whether there is block data for the release
	 */
	boolean known() {
		return registry != null;
	}

	/**
	 * Builds the block a state ID stands for at a position.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @param stateId the global ID of the block's state
	 * @return the block, named where the release's block data names it
	 * @throws IllegalArgumentException when the release's block data has no state with that ID
	 */
	@NotNull SeenBlock block(int x, int y, int z, int stateId) {
		if (registry == null) return new SeenBlock(x, y, z, stateId, null);

		BlockState state = registry.state(stateId);
		BlockIdentity identity = new BlockIdentity(state.getBlock().getName(), state.getProperties(), state.isAir());

		return new SeenBlock(x, y, z, stateId, identity);
	}
}
