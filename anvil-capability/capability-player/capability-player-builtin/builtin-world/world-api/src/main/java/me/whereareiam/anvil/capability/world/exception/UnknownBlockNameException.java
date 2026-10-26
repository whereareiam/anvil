package me.whereareiam.anvil.capability.world.exception;

import lombok.Getter;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import org.jetbrains.annotations.NotNull;

/**
 * Reports that a block's name or properties were asked for, but the block is known only by its state ID, because
 * Anvil ships no block data for the player's Minecraft release. Compare such blocks by their state IDs instead.
 */
@Getter
public class UnknownBlockNameException extends IllegalStateException {
	/**
	 * The block whose name is unknown.
	 */
	private final @NotNull SeenBlock block;

	/**
	 * Creates the failure.
	 *
	 * @param block the block whose name is unknown
	 */
	public UnknownBlockNameException(@NotNull SeenBlock block) {
		super("The block at " + block.getX() + " " + block.getY() + " " + block.getZ() + " is known only as state "
				+ block.getStateId() + ": Anvil ships no block data for the player's Minecraft release, so it knows no "
				+ "block names or properties there; compare state IDs instead");
		this.block = block;
	}
}
