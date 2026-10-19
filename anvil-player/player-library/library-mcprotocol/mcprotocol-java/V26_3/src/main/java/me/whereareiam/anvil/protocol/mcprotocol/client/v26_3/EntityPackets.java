package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSwingAnimation;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.SyncedEntityTranslation;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSwingAnimationPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entities of the MCProtocolLib 26.3 release, whose position sync either places an entity at once or moves it in
 * steps, which the client interpolates; the protocol places the entity where the last step ends. Its server swings an
 * arm with a packet of its own, whose swing style the protocol leaves out.
 */
final class EntityPackets extends SyncedEntityTranslation {
	@Override
	protected @NotNull Vector3d syncedPosition(@NotNull ClientboundEntityPositionSyncPacket sync) {
		if (!sync.isStepped()) return sync.getEndPosition();

		return sync.getSteps().getLast().position();
	}

	@Override
	protected @Nullable ClientboundPacket decodeSwing(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundSwingAnimationPacket swing)) return null;

		InteractionHand hand = swing.getHand() == Hand.MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		return new ClientboundSwingAnimation(swing.getEntityId(), hand);
	}
}
