package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.java.api.model.interaction.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.interaction.InteractionPoint;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib 1.21.1 release, whose item use carries the block-change sequence and the view and
 * whose block use does not yet report whether it hit the world border; attacks and interactions share one entity
 * packet.
 */
final class InteractionPackets implements PacketTranslation<Packet> {
	private static final InteractionPoint ORIGIN = InteractionPoint.builder().build();

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ServerboundSwing.class,
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class
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

		return null;
	}

	private static Packet useItemOn(ServerboundUseItemOn use) {
		BlockPosition position = use.getPosition();
		return new ServerboundUseItemOnPacket(Vector3i.from(position.getX(), position.getY(), position.getZ()),
				direction(use.getFace()), hand(use.getHand()), use.getCursorX(), use.getCursorY(), use.getCursorZ(),
				use.isInsideBlock(), use.getSequence());
	}

	private static Packet interact(ServerboundInteract interact) {
		Hand hand = hand(interact.getHand());
		InteractAction action = action(interact);
		if (action != InteractAction.INTERACT_AT)
			return new ServerboundInteractPacket(interact.getEntityId(), action, hand, interact.isSneaking());

		InteractionPoint point = interact.getLocation() == null ? ORIGIN : interact.getLocation();
		return new ServerboundInteractPacket(interact.getEntityId(), action,
				(float) point.getX(), (float) point.getY(), (float) point.getZ(), hand, interact.isSneaking());
	}

	private static InteractAction action(ServerboundInteract interact) {
		return switch (interact.getAction()) {
			case INTERACT -> InteractAction.INTERACT;
			case ATTACK -> InteractAction.ATTACK;
			case INTERACT_AT -> InteractAction.INTERACT_AT;
		};
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
