package me.whereareiam.anvil.capability.interaction.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.packetlib.Session;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.jetbrains.annotations.NotNull;

import static com.github.steveice10.mc.protocol.data.game.entity.player.Hand.MAIN_HAND;
import static com.github.steveice10.mc.protocol.data.game.entity.player.Hand.OFF_HAND;

/**
 * Interaction packets of the MCProtocolLib release for Minecraft 1.18.2, the first one with serverbound packet
 * names, which still carries neither block-change sequences nor the view of an item use.
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
		session.send(new ServerboundUseItemPacket(nativeHand(hand)));
	}

	@Override
	public void useItemOn(@NotNull Session session, @NotNull BlockPosition position, @NotNull BlockFace face, @NotNull Hand hand, int sequence) {
		session.send(new ServerboundUseItemOnPacket(new Position(position.getX(), position.getY(), position.getZ()),
				direction(face), nativeHand(hand), 0.5F, 0.5F, 0.5F, false));
	}

	@Override
	public void entity(@NotNull Session session, int entityId, @NotNull EntityInteraction kind, @NotNull Hand hand) {
		InteractAction action = switch (kind) {
			case INTERACT -> InteractAction.INTERACT;
			case ATTACK -> InteractAction.ATTACK;
		};
		session.send(new ServerboundInteractPacket(entityId, action, nativeHand(hand), false));
	}

	private static com.github.steveice10.mc.protocol.data.game.entity.player.Hand nativeHand(Hand hand) {
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
