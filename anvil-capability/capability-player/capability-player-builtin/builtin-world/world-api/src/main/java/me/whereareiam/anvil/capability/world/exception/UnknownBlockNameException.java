package me.whereareiam.anvil.capability.world.exception;

import lombok.Getter;
import me.whereareiam.anvil.capability.world.model.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when a test reads the name of a block whose state Anvil cannot name, so that only its state ID is known:
 * Anvil ships no block data for the player's protocol, or the state is not one of vanilla's, such as a modded
 * server's block.
 */
@Getter
public class UnknownBlockNameException extends IllegalStateException {
	/**
	 * The block without a name.
	 */
	private final @NotNull Block block;

	/**
	 * Creates the exception for a block.
	 *
	 * @param block the block without a name
	 */
	public UnknownBlockNameException(@NotNull Block block) {
		super("Anvil cannot name block state " + block.getState() + " of protocol " + block.getProtocol() + " at "
				+ block.getPosition() + ": it ships no block data for the protocol, or the state is not vanilla's");
		this.block = block;
	}
}
