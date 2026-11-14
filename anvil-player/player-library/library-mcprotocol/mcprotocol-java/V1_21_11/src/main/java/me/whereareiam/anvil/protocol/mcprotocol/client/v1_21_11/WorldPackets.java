package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetTime;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.jetbrains.annotations.NotNull;

/**
 * The world of the MCProtocolLib 1.21.11 release, whose time carries the time of day of the player's level and whether
 * the client advances it itself.
 */
public final class WorldPackets extends WorldTranslation {
	@Override
	protected @NotNull ClientboundSetTime time(@NotNull ClientboundSetTimePacket time) {
		return ClientboundSetTime.builder()
				.gameTime(time.getGameTime())
				.dayTime(time.getDayTime())
				.tickDayTime(time.isTickDayTime())
				.build();
	}
}
