package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundRemoveEntities;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundSetPassengers;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundEntityPositionSync;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityPos;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundMoveEntityRot;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundTeleportEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.edition.java.protocol.type.entity.Relative;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ItemTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.EntityTranslation;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ValueTranslator;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Entities of the MCProtocolLib releases from 1.21.11 on, which read entity data with the 1.21.11 layout. Every
 * entity, experience orbs included, spawns through the add-entity packet; teleports name each relative coordinate and
 * angle, and the position sync places and turns an entity absolutely. Each release's {@code EntityPackets} reads where
 * the position sync places the entity, which Minecraft 26.3 may reach in steps. The server sets an entity's velocity,
 * which the library reads in blocks per tick; the protocol's entity teleport carries no velocity, so the relative
 * velocity components a teleport names are left out.
 */
public abstract class SyncedEntityTranslation extends EntityTranslation {
	private static final Set<Relative> VELOCITY = EnumSet.of(Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z,
			Relative.ROTATE_DELTA);

	/**
	 * Creates the translation of one session.
	 *
	 * @param text translates the release's text components
	 * @param items translates the release's item stacks
	 */
	protected SyncedEntityTranslation(@NotNull ComponentTranslator text, @NotNull ItemTranslator items) {
		super(EntityDataTable.LAYOUT, text, items);
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
				ClientboundSetEntityMotion.class,
				ClientboundSetEntityData.class,
				ClientboundSetPassengers.class,
				ClientboundRemoveEntities.class
		);
	}

	@Override
	protected final @Nullable ClientboundPacket decodeRelease(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundAddEntityPacket add -> added(add.getEntityId(), add.getUuid(), add.getType(),
					new Vec3d(add.getX(), add.getY(), add.getZ()), add.getYaw(), add.getPitch());
			case ClientboundTeleportEntityPacket teleport -> ClientboundTeleportEntity.builder()
					.entityId(new EntityId(teleport.getId()))
					.position(ValueTranslator.vec3d(teleport.getPosition()))
					.yaw(teleport.getYRot())
					.pitch(teleport.getXRot())
					.relatives(placement(PlayerPackets.relatives(teleport.getRelatives())))
					.build();
			case ClientboundEntityPositionSyncPacket sync -> ClientboundEntityPositionSync.builder()
					.entityId(new EntityId(sync.getId()))
					.position(ValueTranslator.vec3d(syncedPosition(sync)))
					.yaw(sync.getYRot())
					.pitch(sync.getXRot())
					.build();
			case ClientboundSetEntityMotionPacket motion ->
					new ClientboundSetEntityMotion(new EntityId(motion.getEntityId()), ValueTranslator.vec3d(motion.getMovement()));
			default -> null;
		};
	}

	/**
	 * Reads where a position sync places the entity.
	 *
	 * @param sync the release's position sync
	 * @return the entity's position once the sync is applied
	 */
	protected abstract @NotNull Vector3d syncedPosition(@NotNull ClientboundEntityPositionSyncPacket sync);

	/**
	 * Keeps the coordinates and angles of a teleport's relatives; the protocol's entity teleport carries no velocity.
	 */
	private static Set<Relative> placement(Set<Relative> relatives) {
		Set<Relative> placement = EnumSet.noneOf(Relative.class);
		placement.addAll(relatives);
		placement.removeAll(VELOCITY);
		return Collections.unmodifiableSet(placement);
	}
}
