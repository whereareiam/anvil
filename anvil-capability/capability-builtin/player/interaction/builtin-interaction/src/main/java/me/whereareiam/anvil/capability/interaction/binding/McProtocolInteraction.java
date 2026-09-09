package me.whereareiam.anvil.capability.interaction.binding;

import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.interaction.Interaction;
import me.whereareiam.anvil.capability.interaction.InteractionOperations;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction capability backed by namespaced MCProtocol worker operations.
 */
final class McProtocolInteraction implements Interaction {
	private final CapabilityChannel connection;

	McProtocolInteraction(CapabilityChannel connection) {
		this.connection = connection;
	}

	@Override
	public void useItem(@NotNull Hand hand) {
		connection.request(InteractionOperations.ITEM, new ItemUse(hand));
	}

	@Override
	public void block(@NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand) {
		connection.request(InteractionOperations.BLOCK, new BlockUse(position.getX(), position.getY(), position.getZ(), face, hand));
	}

	@Override
	public void entity(int entityId, @NotNull EntityInteraction interaction, @NotNull Hand hand) {
		connection.request(InteractionOperations.ENTITY, new EntityUse(entityId, interaction, hand));
	}
}
