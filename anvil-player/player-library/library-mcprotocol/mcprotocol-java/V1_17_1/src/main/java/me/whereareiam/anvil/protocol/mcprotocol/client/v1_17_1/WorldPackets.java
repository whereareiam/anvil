package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerSpawnPositionPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.WorldTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The world of the MCProtocolLib 1.17.1 release, which sends the spawn point with the angle a player spawns facing.
 */
public final class WorldPackets extends WorldTranslation {
	@Override
	protected @Nullable ClientboundSetDefaultSpawnPosition spawn(@NotNull Packet packet) {
		if (!(packet instanceof ServerSpawnPositionPacket spawn)) return null;

		return ClientboundSetDefaultSpawnPosition.builder()
				.position(position(spawn.getPosition()))
				.yaw(spawn.getAngle())
				.build();
	}
}
