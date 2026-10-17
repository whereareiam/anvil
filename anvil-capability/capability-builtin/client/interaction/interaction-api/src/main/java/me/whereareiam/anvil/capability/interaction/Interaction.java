package me.whereareiam.anvil.capability.interaction;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.jetbrains.annotations.NotNull;

/**
 * Sends item, block, and entity interactions as a simulated player.
 */
public interface Interaction extends PlayerCapability {
	/**
	 * Swings and uses the selected item.
	 *
	 * @param hand interaction hand
	 */
	void useItem(@NotNull Hand hand);

	/**
	 * Interacts with a block.
	 *
	 * @param position block coordinate
	 * @param face targeted face
	 * @param hand interaction hand
	 */
	void block(@NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand);

	/**
	 * Interacts with a known protocol entity identifier.
	 *
	 * @param entityId protocol entity identifier
	 * @param interaction interaction kind
	 * @param hand hand used for normal interaction
	 */
	void entity(int entityId, @NotNull EntityInteraction interaction, @NotNull Hand hand);
}
