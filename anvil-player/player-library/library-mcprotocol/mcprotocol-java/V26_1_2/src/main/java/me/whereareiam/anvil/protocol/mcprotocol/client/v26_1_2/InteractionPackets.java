package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ValueTranslator;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
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
 * with the hand and the point of the entity, its position when the interaction names none.
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
		return switch (packet) {
			case ServerboundSwing swing -> new ServerboundSwingPacket(ValueTranslator.hand(swing.getHand()));
			case ServerboundUseItem use -> new ServerboundUseItemPacket(ValueTranslator.hand(use.getHand()),
					use.getSequence(), use.getYaw(), use.getPitch());
			case ServerboundUseItemOn use -> useItemOn(use);
			case ServerboundInteract interact -> interact(interact);
			case ServerboundAttack attack -> new ServerboundAttackPacket(attack.getEntityId());
			default -> null;
		};
	}

	private static Packet useItemOn(ServerboundUseItemOn use) {
		BlockPosition position = use.getPosition();
		return new ServerboundUseItemOnPacket(Vector3i.from(position.getX(), position.getY(), position.getZ()),
				ValueTranslator.direction(use.getFace()), ValueTranslator.hand(use.getHand()), use.getCursorX(),
				use.getCursorY(), use.getCursorZ(), use.isInsideBlock(), use.isWorldBorderHit(), use.getSequence());
	}

	private static Packet interact(ServerboundInteract interact) {
		Vec3d point = interact.getPoint() == null ? Vec3d.ZERO : interact.getPoint();
		return new ServerboundInteractPacket(interact.getEntityId(), ValueTranslator.hand(interact.getHand()),
				Vector3d.from(point.getX(), point.getY(), point.getZ()), interact.isSneaking());
	}
}
