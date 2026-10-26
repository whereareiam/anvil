package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
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
	void decodesJoiningALevelAndRespawningIntoLevelsOf256BlocksFromHeightZero() {
		assertEquals(new ClientboundLogin(42, new Dimension("minecraft:overworld", 0, 256)),
				packets.decode(new ServerJoinGamePacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL, 1,
						new String[0], new CompoundTag(""), new CompoundTag(""), "minecraft:overworld", 0, 20, 10, false,
						true, false, false)));
		assertEquals(new ClientboundRespawn(new Dimension("minecraft:the_nether", 0, 256)),
				packets.decode(new ServerRespawnPacket(new CompoundTag(""), "minecraft:the_nether", 0, GameMode.SURVIVAL,
						GameMode.SURVIVAL, false, false, false)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesJoiningALevelAndRespawningButNoConfigurationPhase() {
		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
