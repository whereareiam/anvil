package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.level.notify.GameEvent;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import org.jetbrains.annotations.NotNull;

/**
 * The world of the MCProtocolLib 1.18.2 release, whose spawn point names its block by the library's own position and
 * which names the game's rain events by their meaning before Minecraft named them: its {@code STOP_RAIN} is the game's
 * {@code START_RAINING}, ID 1, and its {@code START_RAIN} the game's {@code STOP_RAINING}, ID 2.
 */
public final class WorldPackets extends WorldTranslation {
	@Override
	protected @NotNull GameEventType rain(@NotNull GameEvent event) {
		return event == GameEvent.STOP_RAIN ? GameEventType.START_RAINING : GameEventType.STOP_RAINING;
	}

	@Override
	protected @NotNull BlockPosition position(@NotNull ClientboundSetDefaultSpawnPositionPacket spawn) {
		Position position = spawn.getPosition();
		return BlockPosition.builder().x(position.getX()).y(position.getY()).z(position.getZ()).build();
	}
}
