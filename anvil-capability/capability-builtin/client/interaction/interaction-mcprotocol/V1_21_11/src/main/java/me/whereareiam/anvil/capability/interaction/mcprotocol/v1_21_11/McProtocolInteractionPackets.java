package me.whereareiam.anvil.capability.interaction.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.NotNull;

import static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.MAIN_HAND;
import static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.OFF_HAND;

/**
 * Interaction packets of the MCProtocolLib release for Minecraft 1.21.11, whose item use carries the view and whose
 * block use reports whether it hit the world border; attacks and interactions still share one entity packet.
 */
public final class McProtocolInteractionPackets implements InteractionPackets<Session> {
	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void swing(@NotNull Session session, @NotNull Hand hand) {
		session.send(new ServerboundSwingPacket(nativeHand(hand)));
	}

	@Override
	public void useItem(@NotNull Session session, @NotNull Hand hand, int sequence, float yaw, float pitch) {
		session.send(new ServerboundUseItemPacket(nativeHand(hand), sequence, yaw, pitch));
	}

	@Override
	public void useItemOn(@NotNull Session session, @NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand, int sequence) {
		session.send(new ServerboundUseItemOnPacket(Vector3i.from(position.getX(), position.getY(), position.getZ()),
				direction(face), nativeHand(hand), 0.5F, 0.5F, 0.5F, false, false, sequence));
	}

	@Override
	public void entity(@NotNull Session session, int entityId, @NotNull EntityInteraction kind, @NotNull Hand hand) {
		InteractAction action = switch (kind) {
			case INTERACT -> InteractAction.INTERACT;
			case ATTACK -> InteractAction.ATTACK;
		};
		session.send(new ServerboundInteractPacket(entityId, action, nativeHand(hand), false));
	}

	private static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand nativeHand(Hand hand) {
		return switch (hand) {
			case MAIN -> MAIN_HAND;
			case OFF -> OFF_HAND;
		};
	}

	private static Direction direction(BlockFace face) {
		return switch (face) {
			case DOWN -> Direction.DOWN;
			case UP -> Direction.UP;
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case WEST -> Direction.WEST;
			case EAST -> Direction.EAST;
		};
	}
}
