package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.CombatState;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerCombatPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The player's health of the MCProtocolLib 1.16.5 release, whose combat packet reports the player's death as its
 * entity-dead state; its other states, entering and leaving combat, are not modelled.
 */
final class HealthPackets extends HealthTranslation {
	HealthPackets(@NotNull ComponentTranslator text) {
		super(text);
	}

	@Override
	protected @Nullable ClientboundPlayerCombatKill decodeDeath(@NotNull Packet packet) {
		if (!(packet instanceof ServerCombatPacket combat) || combat.getCombatState() != CombatState.ENTITY_DEAD)
			return null;

		return new ClientboundPlayerCombatKill(combat.getPlayerId(), text.translate(combat.getMessage()).plain());
	}
}
