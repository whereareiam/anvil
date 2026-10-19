package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundEntityPositionSync;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPos;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundMoveEntityRot;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityMotion;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSwingAnimation;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundTeleportEntity;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.EntityTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ValueTranslator;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Entities of the MCProtocolLib releases from 1.21.11 on, which read entity data with the 1.21.11 layout. Every
 * entity, experience orbs included, spawns through the add-entity packet; teleports name each relative coordinate and
 * angle, while their relative velocity is left out, since the protocol models no entity velocity, and the position sync
 * places and turns an entity absolutely. Each release's {@code EntityPackets} reads where
 * the position sync places the entity, which Minecraft 26.3 may reach in steps, and the swing of an arm, which
 * Minecraft 26.3 moved from the entity's animations to a packet of its own.
 */
public abstract class SyncedEntityTranslation extends EntityTranslation {
	private static final Set<Relative> VELOCITY = EnumSet.of(Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z,
			Relative.ROTATE_DELTA);

	/**
	 * Creates the translation of one session.
	 */
	protected SyncedEntityTranslation() {
		super(EntityDataTable.LAYOUT);
	}

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundAddEntity.class,
				ClientboundMoveEntityPos.class,
				ClientboundMoveEntityPosRot.class,
				ClientboundMoveEntityRot.class,
				ClientboundTeleportEntity.class,
				ClientboundEntityPositionSync.class,
				ClientboundSetEntityData.class,
				ClientboundSetEntityMotion.class,
				ClientboundSetPassengers.class,
				ClientboundSwingAnimation.class,
				ClientboundRemoveEntities.class
		);
	}

	@Override
	protected final @Nullable ClientboundPacket decodeRelease(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundAddEntityPacket add -> added(add.getEntityId(), add.getUuid(), add.getType(),
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ClientboundTeleportEntityPacket teleport -> ClientboundTeleportEntity.builder()
					.entityId(teleport.getId())
					.position(ValueTranslator.vec3d(teleport.getPosition()))
					.yaw(teleport.getYRot())
					.pitch(teleport.getXRot())
					.relatives(placement(teleport.getRelatives()))
					.build();
			case ClientboundEntityPositionSyncPacket sync -> ClientboundEntityPositionSync.builder()
					.entityId(sync.getId())
					.position(ValueTranslator.vec3d(syncedPosition(sync)))
					.yaw(sync.getYRot())
					.pitch(sync.getXRot())
					.build();
			case ClientboundSetEntityMotionPacket motion -> new ClientboundSetEntityMotion(motion.getEntityId(),
					ValueTranslator.vec3d(motion.getMovement()));
			default -> decodeSwing(packet);
		};
	}

	/**
	 * Names the coordinates and angles an entity teleport adds to the current ones.
	 */
	private static Set<Relative> placement(List<PositionElement> elements) {
		Set<Relative> relatives = EnumSet.noneOf(Relative.class);
		relatives.addAll(PlayerPackets.relatives(elements));
		relatives.removeAll(VELOCITY);
		return Collections.unmodifiableSet(relatives);
	}

	/**
	 * Reads a packet that swings an arm of an entity.
	 *
	 * @param packet a packet the release received
	 * @return the swing, or {@code null} when the packet swings no arm
	 */
	protected abstract @Nullable ClientboundPacket decodeSwing(@NotNull Packet packet);

	/**
	 * Reads where a position sync places the entity.
	 *
	 * @param sync the release's position sync
	 * @return the entity's position once the sync is applied
	 */
	protected abstract @NotNull Vector3d syncedPosition(@NotNull ClientboundEntityPositionSyncPacket sync);
}
