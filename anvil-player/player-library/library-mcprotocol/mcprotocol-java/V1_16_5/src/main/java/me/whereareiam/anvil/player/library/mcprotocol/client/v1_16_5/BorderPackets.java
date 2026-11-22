package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerWorldBorderPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDistance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The world border of the MCProtocolLib 1.16.5 release, which sends it as one packet with an action that every release
 * reports as the packet of that action.
 */
public final class BorderPackets extends BorderTranslation {
	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
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
