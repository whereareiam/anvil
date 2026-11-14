package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.ClientRequest;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientRequestPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerCombatKillPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerHealthPacket;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.TextTranslator;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthPacketsTest {
	private final HealthPackets packets = new HealthPackets(new TextTranslator());

	@Test
	void decodesTheHealthAndTheDeathsOwnPacket() {
		assertEquals(new ClientboundSetHealth(0F, 3, 0F), packets.decode(new ServerPlayerHealthPacket(0F, 3, 0F)));
		assertEquals(new ClientboundPlayerCombatKill(42, "Alice fell"),
				packets.decode(new ServerPlayerCombatKillPacket(42, -1, Component.text("Alice fell"))));
	}

	@Test
	void encodesTheRespawnCommand() {
		assertEquals(new ClientRequestPacket(ClientRequest.RESPAWN),
				packets.encode(new ServerboundClientCommand(ClientCommandAction.PERFORM_RESPAWN)));
	}
}
