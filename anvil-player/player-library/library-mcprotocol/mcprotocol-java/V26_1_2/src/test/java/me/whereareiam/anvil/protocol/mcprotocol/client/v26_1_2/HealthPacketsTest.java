package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundPlayerCombatKill;
import me.whereareiam.anvil.protocol.java.api.packet.health.ClientboundSetHealth;
import me.whereareiam.anvil.protocol.java.api.packet.health.ServerboundClientCommand;
import me.whereareiam.anvil.protocol.java.api.type.ClientCommandAction;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.TextTranslator;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
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
				packets.decode(new ClientboundPlayerCombatKillPacket(42,
						Component.translatable("death.attack.genericKill", Component.text("Alice")))));
	}

	@Test
	void encodesTheClientsCommands() {
		assertEquals(new ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN),
				packets.encode(new ServerboundClientCommand(ClientCommandAction.PERFORM_RESPAWN)));
		assertEquals(new ServerboundClientCommandPacket(ClientCommand.REQUEST_STATS),
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
