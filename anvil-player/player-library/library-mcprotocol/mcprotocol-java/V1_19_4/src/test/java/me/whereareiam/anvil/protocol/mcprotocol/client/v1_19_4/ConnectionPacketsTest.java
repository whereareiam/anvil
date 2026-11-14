package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ConnectionPackets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 1.18.2 translation decodes this release's packets, whose dimension type became a key while the level key
 * stayed where it was.
 */
class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void decodesTheDimensionOfThisReleasesPackets() {
		assertEquals(new ClientboundLogin(42, "minecraft:overworld"), packets.decode(new ClientboundLoginPacket(42, false,
				GameMode.SURVIVAL, GameMode.SURVIVAL, new String[0], new CompoundTag(""), "minecraft:overworld",
				"minecraft:overworld", 0, 20, 10, 10, false, true, false, false, null)));
		assertEquals(new ClientboundRespawn("minecraft:the_end"), packets.decode(new ClientboundRespawnPacket(
				"minecraft:the_end", "minecraft:the_end", 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, false,
				false, null)));
	}
}
