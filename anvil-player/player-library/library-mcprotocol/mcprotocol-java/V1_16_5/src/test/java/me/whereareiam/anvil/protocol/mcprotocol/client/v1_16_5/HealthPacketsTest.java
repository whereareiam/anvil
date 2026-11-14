package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.ClientRequest;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientRequestPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerCombatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerHealthPacket;
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
	void decodesTheHealthAndTheDeathStateOfTheCombatPacketWithItsMessage() {
		assertEquals(new ClientboundSetHealth(7.5F, 12, 1F), packets.decode(new ServerPlayerHealthPacket(7.5F, 12, 1F)));
		assertEquals(new ClientboundPlayerCombatKill(42, "death.attack.genericKill Alice"),
				packets.decode(new ServerCombatPacket(43, 42,
						Component.translatable("death.attack.genericKill", Component.text("Alice")))));
	}

	@Test
	void leavesEnteringAndLeavingCombatAndOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerCombatPacket()));
		assertNull(packets.decode(new ServerCombatPacket(42, 20)));
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void encodesTheClientsCommands() {
		assertEquals(new ClientRequestPacket(ClientRequest.RESPAWN),
				packets.encode(new ServerboundClientCommand(ClientCommandAction.PERFORM_RESPAWN)));
		assertEquals(new ClientRequestPacket(ClientRequest.STATS),
				packets.encode(new ServerboundClientCommand(ClientCommandAction.REQUEST_STATS)));
		assertEquals(Set.of(ClientboundSetHealth.class, ClientboundPlayerCombatKill.class, ServerboundClientCommand.class),
				packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
