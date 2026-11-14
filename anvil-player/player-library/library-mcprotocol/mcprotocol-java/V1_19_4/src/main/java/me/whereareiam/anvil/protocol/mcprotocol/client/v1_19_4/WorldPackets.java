package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.level.notify.GameEvent;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.WorldTranslation;
import org.cloudburstmc.math.vector.Vector3i;
import org.jetbrains.annotations.NotNull;

/**
 * The world of the MCProtocolLib 1.19.4 release, whose spawn point names its block by a vector and whose rain events
 * have the game's IDs.
 */
final class WorldPackets extends WorldTranslation {
	@Override
	protected @NotNull GameEventType rain(@NotNull GameEvent event) {
		return event == GameEvent.START_RAIN ? GameEventType.START_RAINING : GameEventType.STOP_RAINING;
	}

	@Override
	protected @NotNull BlockPosition position(@NotNull ClientboundSetDefaultSpawnPositionPacket spawn) {
		Vector3i position = spawn.getPosition();
		return BlockPosition.builder().x(position.getX()).y(position.getY()).z(position.getZ()).build();
	}
}
