package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPlayerPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.EntityTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entities of the MCProtocolLib 1.19.4 release, which spawns mobs and paintings through the entity packet and keeps
 * packets of their own only for players and experience orbs; the translation reports each of them as an added entity,
 * an orb facing no direction and without a unique ID.
 */
public final class EntityPackets extends EntityTranslation {
	private static final float NO_DIRECTION = 0;

	/**
	 * Creates the translation of the release's entities.
	 */
	public EntityPackets() {
		super(EntityDataTable.LAYOUT);
	}

	@Override
	protected @Nullable ClientboundPacket decodeSpawn(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundAddEntityPacket add -> added(add.getEntityId(), add.getUuid(), add.getType(),
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ClientboundAddPlayerPacket add -> added(add.getEntityId(), add.getUuid(), EntityType.PLAYER,
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ClientboundAddExperienceOrbPacket orb -> added(orb.getEntityId(), null, EntityType.EXPERIENCE_ORB,
					new Vec3d(orb.getX(), orb.getY(), orb.getZ()), NO_DIRECTION, NO_DIRECTION);
			default -> null;
		};
	}
}
