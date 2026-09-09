package me.whereareiam.anvil.capability.interaction;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;

/**
 * Typed interaction operations implemented by native capability bindings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InteractionOperations {
	/**
	 * Executes the item operation with a registered request schema.
	 */
	public static final ChannelOperation<ItemUse, Void> ITEM = new ChannelOperation<>("interaction.item", ItemUse.class, Void.class);
	/**
	 * Executes the block operation with a registered request schema.
	 */
	public static final ChannelOperation<BlockUse, Void> BLOCK = new ChannelOperation<>("interaction.block", BlockUse.class, Void.class);
	/**
	 * Executes the entity operation with a registered request schema.
	 */
	public static final ChannelOperation<EntityUse, Void> ENTITY = new ChannelOperation<>("interaction.entity", EntityUse.class, Void.class);
}
