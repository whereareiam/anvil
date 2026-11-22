package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
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
 * The world border of the MCProtocolLib releases from 1.21.11 on, which move the border in ticks as the game does.
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
					.lerpTime(border.getLerpTime())
					.absoluteMaxSize(border.getNewAbsoluteMaxSize())
					.warningBlocks(border.getWarningBlocks())
					.warningTime(border.getWarningTime())
					.build();
			case ClientboundSetBorderCenterPacket center -> new ClientboundSetBorderCenter(center.getNewCenterX(), center.getNewCenterZ());
			case ClientboundSetBorderLerpSizePacket lerp -> new ClientboundSetBorderLerpSize(lerp.getOldSize(), lerp.getNewSize(),
					lerp.getLerpTime());
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
}
