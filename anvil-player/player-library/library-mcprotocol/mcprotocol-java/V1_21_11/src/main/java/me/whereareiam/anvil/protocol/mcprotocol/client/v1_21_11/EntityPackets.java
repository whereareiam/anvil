package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSwingAnimation;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Animation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAnimatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entities of the MCProtocolLib 1.21.11 release, whose position sync carries the entity's position and whose server
 * swings an arm with an animation of the entity.
 */
public final class EntityPackets extends SyncedEntityTranslation {
	@Override
	protected @NotNull Vector3d syncedPosition(@NotNull ClientboundEntityPositionSyncPacket sync) {
		return sync.getPosition();
	}

	@Override
	protected @Nullable ClientboundPacket decodeSwing(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundAnimatePacket animation)) return null;

		if (animation.getAnimation() == Animation.SWING_ARM)
			return new ClientboundSwingAnimation(animation.getEntityId(), InteractionHand.MAIN_HAND);
		if (animation.getAnimation() == Animation.SWING_OFFHAND)
			return new ClientboundSwingAnimation(animation.getEntityId(), InteractionHand.OFF_HAND);

		return null;
	}
}
