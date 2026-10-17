package me.whereareiam.anvil.capability.interaction;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.interaction.channel.InteractionChannel;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction capability driven over a player's typed channel: each action is one request to the worker's
 * interaction extension, which sends the packets.
 */
@RequiredArgsConstructor
final class ChannelInteraction implements Interaction {
	private final @NotNull ProtocolChannel channel;

	@Override
	public void useItem(@NotNull Hand hand) {
		channel.request(InteractionChannel.ITEM, new ItemUse(hand));
	}

	@Override
	public void block(@NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand) {
		channel.request(InteractionChannel.BLOCK, new BlockUse(position.getX(), position.getY(), position.getZ(), face, hand));
	}

	@Override
	public void entity(int entityId, @NotNull EntityInteraction interaction, @NotNull Hand hand) {
		channel.request(InteractionChannel.ENTITY, new EntityUse(entityId, interaction, hand));
	}
}
