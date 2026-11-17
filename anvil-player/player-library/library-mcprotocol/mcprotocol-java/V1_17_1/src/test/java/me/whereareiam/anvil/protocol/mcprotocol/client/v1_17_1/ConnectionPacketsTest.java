package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.ConnectionPackets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The connection translation of the 1.16 chain on the MCProtocolLib 1.17.1 release, which reads the game mode of the
 * login and the respawn into the library's game mode as 1.16.5 does.
 */
class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void carriesTheGameModeOfTheLogin() {
		assertEquals(me.whereareiam.anvil.protocol.java.api.type.player.GameMode.CREATIVE,
				((ClientboundLogin) packets.decode(login(GameMode.CREATIVE))).getGameMode());
		assertEquals(me.whereareiam.anvil.protocol.java.api.type.player.GameMode.SURVIVAL,
				((ClientboundLogin) packets.decode(login(GameMode.UNKNOWN))).getGameMode());
	}

	@Test
	void carriesTheGameModeOfTheRespawn() {
		packets.decode(login(GameMode.SURVIVAL));

		assertEquals(me.whereareiam.anvil.protocol.java.api.type.player.GameMode.SPECTATOR,
				((ClientboundRespawn) packets.decode(new ServerRespawnPacket(overworld(), "minecraft:overworld", 0,
						GameMode.SPECTATOR, GameMode.SURVIVAL, false, false, true))).getGameMode());
		assertEquals(me.whereareiam.anvil.protocol.java.api.type.player.GameMode.ADVENTURE,
				((ClientboundRespawn) packets.decode(new ServerRespawnPacket(overworld(), "minecraft:overworld", 0,
						GameMode.ADVENTURE, GameMode.SPECTATOR, false, false, true))).getGameMode());
	}

	private static ServerJoinGamePacket login(GameMode mode) {
		return new ServerJoinGamePacket(42, false, mode, GameMode.UNKNOWN, 1, new String[]{"minecraft:overworld"},
				codec(), overworld(), "minecraft:overworld", 0, 20, 10, false, true, false, false);
	}

	private static CompoundTag overworld() {
		CompoundTag type = new CompoundTag("");
		type.put(new StringTag("effects", "minecraft:overworld"));
		type.put(new IntTag("min_y", -64));
		type.put(new IntTag("height", 384));
		return type;
	}

	private static CompoundTag codec() {
		CompoundTag entry = new CompoundTag("");
		entry.put(new StringTag("name", "minecraft:overworld"));
		entry.put(new IntTag("id", 0));
		entry.put(new CompoundTag("element", overworld().getValue()));
		CompoundTag registry = new CompoundTag("minecraft:dimension_type");
		registry.put(new StringTag("type", "minecraft:dimension_type"));
		registry.put(new ListTag("value", List.<Tag>of(entry)));
		CompoundTag codec = new CompoundTag("");
		codec.put(registry);
		return codec;
	}
}
