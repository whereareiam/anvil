package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerSpawnPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerInitializeBorderPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderCenterPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderLerpSizePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderSizePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderWarningDelayPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderWarningDistancePacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundInitializeBorder;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderCenter;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.WorldTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The world of the MCProtocolLib 1.17.1 release, which sends a packet per change of the world border and the spawn
 * point with the angle a player spawns facing.
 */
public final class WorldPackets extends WorldTranslation {
	@Override
	protected @Nullable ClientboundPacket borderOrSpawn(@NotNull Packet packet) {
		return switch (packet) {
			case ServerInitializeBorderPacket border -> ClientboundInitializeBorder.builder()
					.centerX(border.getNewCenterX())
					.centerZ(border.getNewCenterZ())
					.oldSize(border.getOldSize())
					.newSize(border.getNewSize())
					.lerpTime(ticks(border.getLerpTime()))
					.absoluteMaxSize(border.getNewAbsoluteMaxSize())
					.warningBlocks(border.getWarningBlocks())
					.warningTime(border.getWarningTime())
					.build();
			case ServerSetBorderCenterPacket center -> new ClientboundSetBorderCenter(center.getNewCenterX(), center.getNewCenterZ());
			case ServerSetBorderLerpSizePacket lerp -> new ClientboundSetBorderLerpSize(lerp.getOldSize(), lerp.getNewSize(),
					ticks(lerp.getLerpTime()));
			case ServerSetBorderSizePacket size -> new ClientboundSetBorderSize(size.getSize());
			case ServerSetBorderWarningDelayPacket delay -> new ClientboundSetBorderWarningDelay(delay.getWarningDelay());
			case ServerSetBorderWarningDistancePacket distance -> new ClientboundSetBorderWarningDistance(distance.getWarningBlocks());
			case ServerSpawnPositionPacket spawn -> ClientboundSetDefaultSpawnPosition.builder()
					.position(position(spawn.getPosition()))
					.yaw(spawn.getAngle())
					.build();
			default -> null;
		};
	}
}
