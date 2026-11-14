package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.ClientCommand;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HealthPacketsTest {
	private final HealthPackets packets = new HealthPackets(new TextTranslator());

	@Test
	void decodesTheHealthAndTheDeathWithItsMessage() {
		assertEquals(new ClientboundSetHealth(7.5F, 12, 1F), packets.decode(new ClientboundSetHealthPacket(7.5F, 12, 1F)));
		assertEquals(new ClientboundPlayerCombatKill(42, "death.attack.genericKill Alice"),
				packets.decode(new ClientboundPlayerCombatKillPacket(42, 43,
						Component.translatable("death.attack.genericKill", Component.text("Alice")))));
	}

	@Test
	void encodesTheClientsCommands() {
		assertEquals(new ServerboundClientCommandPacket(ClientCommand.RESPAWN),
				packets.encode(new ServerboundClientCommand(ClientCommandAction.PERFORM_RESPAWN)));
		assertEquals(new ServerboundClientCommandPacket(ClientCommand.STATS),
				packets.encode(new ServerboundClientCommand(ClientCommandAction.REQUEST_STATS)));
	}

	@Test
	void carriesTheHealthAreaOnly() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
		assertEquals(Set.of(ClientboundSetHealth.class, ClientboundPlayerCombatKill.class, ServerboundClientCommand.class),
				packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
