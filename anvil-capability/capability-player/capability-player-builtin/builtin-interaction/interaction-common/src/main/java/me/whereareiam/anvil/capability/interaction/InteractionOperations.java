package me.whereareiam.anvil.capability.interaction;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * Typed interaction operations shared by the host provider and the worker's interaction binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InteractionOperations {
	/**
	 * Swings the arm and uses the item held in a hand.
	 */
	public static final OperationDescriptor<ItemUse, Void> ITEM = new OperationDescriptor<>(
			"interaction.item", ItemUse.class, Void.class
	);
	/**
	 * Uses the item held in a hand on a block face, then swings the arm.
	 */
	public static final OperationDescriptor<BlockUse, Void> BLOCK = new OperationDescriptor<>(
			"interaction.block", BlockUse.class, Void.class
	);
	/**
	 * Interacts with or attacks an entity, then swings the arm.
	 */
	public static final OperationDescriptor<EntityUse, Void> ENTITY = new OperationDescriptor<>(
			"interaction.entity", EntityUse.class, Void.class
	);
}
