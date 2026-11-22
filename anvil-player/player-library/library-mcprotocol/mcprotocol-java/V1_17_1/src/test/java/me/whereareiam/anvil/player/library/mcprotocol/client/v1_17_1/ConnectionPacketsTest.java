package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPongPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundPing;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ServerboundPong;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connection translation of the 1.16 chain on the MCProtocolLib 1.17.1 release, which reads the game mode of the
 * login and the respawn into the library's game mode as 1.16.5 does, and fast lava from a type's {@code ultrawarm}, and
 * carries the ping Minecraft 1.17 introduced with its pong.
 */
class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void carriesTheGameModeOfTheLogin() {
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.CREATIVE,
				((ClientboundLogin) packets.decode(login(GameMode.CREATIVE))).getGameMode());
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SURVIVAL,
				((ClientboundLogin) packets.decode(login(GameMode.UNKNOWN))).getGameMode());
	}

	@Test
	void carriesTheGameModeOfTheRespawn() {
		packets.decode(login(GameMode.SURVIVAL));

		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SPECTATOR,
				((ClientboundRespawn) packets.decode(new ServerRespawnPacket(overworld(), "minecraft:overworld", 0,
						GameMode.SPECTATOR, GameMode.SURVIVAL, false, false, true))).getGameMode());
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.ADVENTURE,
				((ClientboundRespawn) packets.decode(new ServerRespawnPacket(overworld(), "minecraft:overworld", 0,
						GameMode.ADVENTURE, GameMode.SPECTATOR, false, false, true))).getGameMode());
	}

	@Test
	void carriesThePingAndItsPong() {
		assertEquals(new ClientboundPing(-7), packets.decode(new ServerPingPacket(-7)));
		assertEquals(-7, assertInstanceOf(ClientPongPacket.class, packets.encode(new ServerboundPong(-7))).getId());
	}

	@Test
	void readsFastLavaFromTheUltrawarmFlagOfTheType() {
		packets.decode(login(GameMode.SURVIVAL));

		assertFalse(((ClientboundLogin) packets.decode(login(GameMode.SURVIVAL))).getDimensionType().isFastLava());
		assertTrue(((ClientboundRespawn) packets.decode(new ServerRespawnPacket(nether(), "minecraft:the_nether", 0,
				GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, true))).getDimensionType().isFastLava());
	}

	private static ServerJoinGamePacket login(GameMode mode) {
		return new ServerJoinGamePacket(42, false, mode, GameMode.UNKNOWN, 1,
				new String[]{"minecraft:overworld", "minecraft:the_nether"}, codec(), overworld(),
				"minecraft:overworld", 0, 20, 10, false, true, false, false);
	}

	private static CompoundTag overworld() {
		return type("minecraft:overworld", -64, 384, false);
	}

	private static CompoundTag nether() {
		return type("minecraft:the_nether", 0, 256, true);
	}

	private static CompoundTag type(String effects, int minY, int height, boolean ultrawarm) {
		CompoundTag type = new CompoundTag("");
		type.put(new StringTag("effects", effects));
		type.put(new IntTag("min_y", minY));
		type.put(new IntTag("height", height));
		type.put(new ByteTag("ultrawarm", (byte) (ultrawarm ? 1 : 0)));
		return type;
	}

	private static CompoundTag codec() {
		CompoundTag registry = new CompoundTag("minecraft:dimension_type");
		registry.put(new StringTag("type", "minecraft:dimension_type"));
		registry.put(new ListTag("value", List.<Tag>of(entry("minecraft:overworld", 0, overworld()),
				entry("minecraft:the_nether", 1, nether()))));
		CompoundTag codec = new CompoundTag("");
		codec.put(registry);
		return codec;
	}

	private static CompoundTag entry(String name, int id, CompoundTag type) {
		CompoundTag entry = new CompoundTag("");
		entry.put(new StringTag("name", name));
		entry.put(new IntTag("id", id));
		entry.put(new CompoundTag("element", type.getValue()));
		return entry;
	}
}
