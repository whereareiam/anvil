package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.SyncedEntityTranslation;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Entities of the MCProtocolLib 26.3 release, whose position sync either places an entity at once or moves it in
 * steps, which the client interpolates; the protocol places the entity where the last step ends.
 */
final class EntityPackets extends SyncedEntityTranslation {
	@Override
	protected @NotNull Vector3d syncedPosition(@NotNull ClientboundEntityPositionSyncPacket sync) {
		if (!sync.isStepped()) return sync.getEndPosition();

		return sync.getSteps().getLast().position();
	}
}
