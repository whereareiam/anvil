package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerSpawnPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerWorldBorderPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundInitializeBorder;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderCenter;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetDefaultSpawnPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The world of the MCProtocolLib 1.16.5 release, which sends its world border as one packet with an action that every
 * release reports as the packet of that action, and its spawn point without an angle.
 */
public final class WorldPackets extends WorldTranslation {
	@Override
	protected @Nullable ClientboundPacket borderOrSpawn(@NotNull Packet packet) {
		if (packet instanceof ServerSpawnPositionPacket spawn)
			return ClientboundSetDefaultSpawnPosition.builder().position(position(spawn.getPosition())).build();
		if (!(packet instanceof ServerWorldBorderPacket border)) return null;

		return switch (border.getAction()) {
			case INITIALIZE -> ClientboundInitializeBorder.builder()
					.centerX(border.getNewCenterX())
					.centerZ(border.getNewCenterZ())
					.oldSize(border.getOldSize())
					.newSize(border.getNewSize())
					.lerpTime(ticks(border.getLerpTime()))
					.absoluteMaxSize(border.getNewAbsoluteMaxSize())
					.warningBlocks(border.getWarningBlocks())
					.warningTime(border.getWarningTime())
					.build();
			case SET_SIZE -> new ClientboundSetBorderSize(border.getNewSize());
			case LERP_SIZE -> new ClientboundSetBorderLerpSize(border.getOldSize(), border.getNewSize(),
					ticks(border.getLerpTime()));
			case SET_CENTER -> new ClientboundSetBorderCenter(border.getNewCenterX(), border.getNewCenterZ());
			case SET_WARNING_TIME -> new ClientboundSetBorderWarningDelay(border.getWarningTime());
			case SET_WARNING_BLOCKS -> new ClientboundSetBorderWarningDistance(border.getWarningBlocks());
		};
	}
}
