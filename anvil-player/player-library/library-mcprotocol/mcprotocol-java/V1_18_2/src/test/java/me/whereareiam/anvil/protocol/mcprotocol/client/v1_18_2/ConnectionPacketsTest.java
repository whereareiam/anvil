package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void decodesJoiningALevelAndRespawningWithTheDimension() {
		assertEquals(new ClientboundLogin(42, "minecraft:overworld"), packets.decode(new ClientboundLoginPacket(42, false,
				GameMode.SURVIVAL, GameMode.SURVIVAL, 1, new String[0], new CompoundTag(""), new CompoundTag(""),
				"minecraft:overworld", 0, 20, 10, 10, false, true, false, false)));
		assertEquals(new ClientboundRespawn("minecraft:the_nether"), packets.decode(new ClientboundRespawnPacket(
				new CompoundTag(""), "minecraft:the_nether", 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, false)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesJoiningALevelAndRespawningButNoConfigurationPhase() {
		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
