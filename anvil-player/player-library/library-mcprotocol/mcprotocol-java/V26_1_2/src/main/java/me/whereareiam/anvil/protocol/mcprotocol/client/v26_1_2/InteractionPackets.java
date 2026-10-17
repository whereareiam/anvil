package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.model.interaction.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.interaction.InteractionPoint;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.type.InteractAction;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib 26.1 release, which sends an attack in a packet of its own and every interaction
 * with the hand and the point on the entity, its position when the interaction names none.
 */
final class InteractionPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ServerboundSwing.class,
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class,
				ServerboundAttack.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundSwing swing) return new ServerboundSwingPacket(hand(swing.getHand()));
		if (packet instanceof ServerboundUseItem use)
			return new ServerboundUseItemPacket(hand(use.getHand()), use.getSequence(), use.getYaw(), use.getPitch());
		if (packet instanceof ServerboundUseItemOn use) return useItemOn(use);
		if (packet instanceof ServerboundInteract interact) return interact(interact);
		if (packet instanceof ServerboundAttack attack) return new ServerboundAttackPacket(attack.getEntityId());

		return null;
	}

	private static Packet useItemOn(ServerboundUseItemOn use) {
		BlockPosition position = use.getPosition();
		return new ServerboundUseItemOnPacket(Vector3i.from(position.getX(), position.getY(), position.getZ()),
				direction(use.getFace()), hand(use.getHand()), use.getCursorX(), use.getCursorY(), use.getCursorZ(),
				use.isInsideBlock(), use.isWorldBorderHit(), use.getSequence());
	}

	private static Packet interact(ServerboundInteract interact) {
		if (interact.getAction() == InteractAction.ATTACK)
			throw new ProtocolUnavailableException("Minecraft 26.1 sends an attack as " + ServerboundAttack.class.getName());

		InteractionPoint point = interact.getLocation();
		Vector3d location = point == null ? Vector3d.ZERO : Vector3d.from(point.getX(), point.getY(), point.getZ());
		return new ServerboundInteractPacket(interact.getEntityId(), hand(interact.getHand()), location, interact.isSneaking());
	}

	private static Hand hand(InteractionHand hand) {
		return switch (hand) {
			case MAIN_HAND -> Hand.MAIN_HAND;
			case OFF_HAND -> Hand.OFF_HAND;
		};
	}

	private static Direction direction(me.whereareiam.anvil.protocol.java.api.type.Direction face) {
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
