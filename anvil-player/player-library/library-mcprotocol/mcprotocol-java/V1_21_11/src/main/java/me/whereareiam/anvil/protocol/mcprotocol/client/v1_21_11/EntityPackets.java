package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Entities of the MCProtocolLib 1.21.11 release, whose position sync carries the entity's position.
 */
public final class EntityPackets extends SyncedEntityTranslation {
	@Override
	protected @NotNull Vector3d syncedPosition(@NotNull ClientboundEntityPositionSyncPacket sync) {
		return sync.getPosition();
	}
}
