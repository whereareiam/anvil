package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityPos;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityRot;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundTeleportEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Entities of the MCProtocolLib 1.20.6 and 1.21.1 releases. Experience orbs still spawn through a packet of their own,
 * without a unique ID or direction, which the translation reports as an added entity, teleports place and turn an
 * entity absolutely, and a velocity arrives as three components in blocks per tick, which the library reads from the
 * 1/8000 of a block per tick that the wire carries.
 */
public final class EntityPackets extends EntityTranslation {
	private static final float NO_DIRECTION = 0;

	/**
	 * Creates the translation.
	 */
	public EntityPackets(@NotNull ComponentTranslator text, @NotNull ItemTranslator items) {
		super(EntityDataTable.LAYOUT, text, items);
	}

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundAddEntity.class,
				ClientboundMoveEntityPos.class,
				ClientboundMoveEntityPosRot.class,
				ClientboundMoveEntityRot.class,
				ClientboundTeleportEntity.class,
				ClientboundSetEntityMotion.class,
				ClientboundSetEntityData.class,
				ClientboundSetPassengers.class,
				ClientboundRemoveEntities.class
		);
	}

	@Override
	protected @Nullable ClientboundPacket decodeRelease(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundAddEntityPacket add -> added(add.getEntityId(), add.getUuid(), add.getType(),
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ClientboundAddExperienceOrbPacket orb -> added(orb.getEntityId(), null, EntityType.EXPERIENCE_ORB,
					new Vec3d(orb.getX(), orb.getY(), orb.getZ()), NO_DIRECTION, NO_DIRECTION);
			case ClientboundTeleportEntityPacket teleport -> ClientboundTeleportEntity.builder()
					.entityId(new EntityId(teleport.getEntityId()))
					.position(new Vec3d(teleport.getX(), teleport.getY(), teleport.getZ()))
					.yaw(teleport.getYaw())
					.pitch(teleport.getPitch())
					.relatives(Set.of())
					.build();
			case ClientboundSetEntityMotionPacket motion -> new ClientboundSetEntityMotion(new EntityId(motion.getEntityId()),
					new Vec3d(motion.getMotionX(), motion.getMotionY(), motion.getMotionZ()));
			default -> null;
		};
	}
}
