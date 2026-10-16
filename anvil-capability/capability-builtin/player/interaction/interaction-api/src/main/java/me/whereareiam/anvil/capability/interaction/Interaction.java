package me.whereareiam.anvil.capability.interaction;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.jetbrains.annotations.NotNull;

/**
 * Sends item, block, and entity interactions as a simulated player.
 *
 * <p>A Bedrock player uses the item of the hotbar slot it holds, or of its off hand, from its current position. It
 * targets a block by the block's runtime ID from the world the server sent it, so a block use fails until the player
 * has received the block. Bedrock versions before 1.26.60 act with the main hand only and refuse
 * {@link Hand#OFF}.</p>
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
	 * Interacts with or attacks an entity by the identifier the protocol gives it.
	 *
	 * <p>The identifier is the one the server sent this player: a Java Edition entity ID, which is an {@code int},
	 * or a Bedrock Edition runtime entity ID, which is a {@code long}. A Java Edition player refuses an identifier
	 * outside the {@code int} range.</p>
	 *
	 * <pre>{@code
	 * interaction.entity(entityId, EntityInteraction.ATTACK, Hand.MAIN);
	 * }</pre>
	 *
	 * @param entityId protocol identifier of the targeted entity
	 * @param interaction whether the player interacts with or attacks the entity
	 * @param hand hand used for normal interaction
	 * @throws IllegalArgumentException when the player's protocol cannot carry the identifier
	 */
	void entity(long entityId, @NotNull EntityInteraction interaction, @NotNull Hand hand);
}
