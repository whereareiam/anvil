package me.whereareiam.anvil.capability.interaction;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;

/**
 * Typed interaction operations shared by the host provider and the worker's interaction binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InteractionOperations {
	/**
	 * Swings the arm and uses the item held in a hand.
	 */
	public static final ChannelOperation<ItemUse, Void> ITEM = new ChannelOperation<>("interaction.item", ItemUse.class, Void.class);
	/**
	 * Uses the item held in a hand on a block face, then swings the arm.
	 */
	public static final ChannelOperation<BlockUse, Void> BLOCK = new ChannelOperation<>("interaction.block", BlockUse.class, Void.class);
	/**
	 * Interacts with or attacks an entity, then swings the arm.
	 */
	public static final ChannelOperation<EntityUse, Void> ENTITY = new ChannelOperation<>("interaction.entity", EntityUse.class, Void.class);
}
