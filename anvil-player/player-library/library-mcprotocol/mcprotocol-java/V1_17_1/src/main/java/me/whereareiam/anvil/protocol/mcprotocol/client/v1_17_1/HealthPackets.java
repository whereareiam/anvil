package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerCombatKillPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.HealthTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The player's health of the MCProtocolLib 1.17.1 release, whose death has the packet of its own that Minecraft 1.17
 * gave it.
 */
final class HealthPackets extends HealthTranslation {
	HealthPackets(@NotNull ComponentTranslator text) {
		super(text);
	}

	@Override
	protected @Nullable ClientboundPlayerCombatKill decodeDeath(@NotNull Packet packet) {
		if (!(packet instanceof ServerPlayerCombatKillPacket kill)) return null;

		return new ClientboundPlayerCombatKill(kill.getPlayerId(), text.translate(kill.getMessage()).plain());
	}
}
