package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderLerpSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDelayPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDistancePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The world border of the MCProtocolLib releases from 1.20.5 until 1.21.1, which move the border in milliseconds that
 * every release reports as ticks.
 */
public final class BorderPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundInitializeBorder.class,
				ClientboundSetBorderCenter.class,
				ClientboundSetBorderLerpSize.class,
				ClientboundSetBorderSize.class,
				ClientboundSetBorderWarningDelay.class,
				ClientboundSetBorderWarningDistance.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundInitializeBorderPacket border -> ClientboundInitializeBorder.builder()
					.centerX(border.getNewCenterX())
					.centerZ(border.getNewCenterZ())
					.oldSize(border.getOldSize())
					.newSize(border.getNewSize())
					.lerpTime(ticks(border.getLerpTime()))
					.absoluteMaxSize(border.getNewAbsoluteMaxSize())
					.warningBlocks(border.getWarningBlocks())
					.warningTime(border.getWarningTime())
					.build();
			case ClientboundSetBorderCenterPacket center -> new ClientboundSetBorderCenter(center.getNewCenterX(), center.getNewCenterZ());
			case ClientboundSetBorderLerpSizePacket lerp -> new ClientboundSetBorderLerpSize(lerp.getOldSize(), lerp.getNewSize(),
					ticks(lerp.getLerpTime()));
			case ClientboundSetBorderSizePacket size -> new ClientboundSetBorderSize(size.getSize());
			case ClientboundSetBorderWarningDelayPacket delay -> new ClientboundSetBorderWarningDelay(delay.getWarningDelay());
			case ClientboundSetBorderWarningDistancePacket distance -> new ClientboundSetBorderWarningDistance(distance.getWarningBlocks());
			default -> null;
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Converts the milliseconds a border of these releases moves for into ticks.
	 */
	private static long ticks(long millis) {
		return millis / 50;
	}
}
