package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerSpawnPositionPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetDefaultSpawnPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The world of the MCProtocolLib 1.16.5 release, which sends its spawn point without an angle.
 */
public final class WorldPackets extends WorldTranslation {
	@Override
	protected @Nullable ClientboundSetDefaultSpawnPosition spawn(@NotNull Packet packet) {
		if (!(packet instanceof ServerSpawnPositionPacket spawn)) return null;

		return ClientboundSetDefaultSpawnPosition.builder().position(position(spawn.getPosition())).build();
	}
}
