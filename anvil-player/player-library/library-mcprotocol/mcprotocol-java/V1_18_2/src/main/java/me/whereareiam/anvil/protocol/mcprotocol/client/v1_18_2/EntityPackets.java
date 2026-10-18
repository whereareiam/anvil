package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddMobPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPaintingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPlayerPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entities of the MCProtocolLib 1.18.2 release, which spawns mobs, players, paintings and experience orbs through
 * packets of their own; the translation reports each of them as an added entity, a painting at the corner of the
 * block it hangs at and facing no direction, as it does an orb, which has no unique ID either.
 */
final class EntityPackets extends EntityTranslation {
	private static final float NO_DIRECTION = 0;

	/**
	 * Creates the translation of the release's entities.
	 */
	EntityPackets() {
		super(EntityDataTable.LAYOUT);
	}

	@Override
	protected @Nullable ClientboundPacket decodeSpawn(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundAddEntityPacket add -> added(add.getEntityId(), add.getUuid(), add.getType(),
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ClientboundAddMobPacket add -> added(add.getEntityId(), add.getUuid(), add.getType(),
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ClientboundAddPlayerPacket add -> added(add.getEntityId(), add.getUuid(), EntityType.PLAYER,
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ClientboundAddPaintingPacket add -> added(add.getEntityId(), add.getUuid(), EntityType.PAINTING,
					corner(add.getPosition()), NO_DIRECTION, NO_DIRECTION);
			case ClientboundAddExperienceOrbPacket orb -> added(orb.getEntityId(), null, EntityType.EXPERIENCE_ORB,
					new Vec3d(orb.getX(), orb.getY(), orb.getZ()), NO_DIRECTION, NO_DIRECTION);
			default -> null;
		};
	}

	/**
	 * Places a painting at the corner of the block it hangs at.
	 */
	private static Vec3d corner(Position block) {
		return new Vec3d(block.getX(), block.getY(), block.getZ());
	}
}
