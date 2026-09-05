package me.whereareiam.anvil.capability.interaction.internal;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.interaction.Interaction;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction capability backed by namespaced MCProtocol worker operations.
 */
final class McProtocolInteraction implements Interaction {
	private final ProtocolPlayerConnection connection;

	McProtocolInteraction(ProtocolPlayerConnection connection) {
		this.connection = connection;
	}

	@Override
	public void useItem(@NotNull Hand hand) {
		connection.execute("interaction.item", arguments -> arguments.put("hand", hand.name()));
	}

	@Override
	public void block(@NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand) {
		connection.execute("interaction.block", arguments -> {
			arguments.put("x", position.getX());
			arguments.put("y", position.getY());
			arguments.put("z", position.getZ());
			arguments.put("face", face.name());
			arguments.put("hand", hand.name());
		});
	}

	@Override
	public void entity(int entityId, @NotNull EntityInteraction interaction, @NotNull Hand hand) {
		connection.execute("interaction.entity", arguments -> {
			arguments.put("entityId", entityId);
			arguments.put("interaction", interaction.name());
			arguments.put("hand", hand.name());
		});
	}
}
