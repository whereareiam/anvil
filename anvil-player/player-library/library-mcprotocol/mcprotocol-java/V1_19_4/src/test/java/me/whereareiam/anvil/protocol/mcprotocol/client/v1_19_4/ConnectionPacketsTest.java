package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets(new ConnectionLevel());

	@Test
	void decodesJoiningALevelAndRespawningWithTheHeightOfTheDimensionTypeTheLoginsRegistriesGive() {
		assertEquals(new ClientboundLogin(42, new Dimension("minecraft:overworld", -64, 384)),
				packets.decode(login("minecraft:overworld", "minecraft:overworld")));
		assertEquals(new ClientboundRespawn(new Dimension("minecraft:the_nether", 0, 256)),
				packets.decode(respawn("minecraft:the_nether", "minecraft:the_nether")));
	}

	@Test
	void refusesADimensionTypeTheRegistriesDoNotHold() {
		packets.decode(login("minecraft:overworld", "minecraft:overworld"));

		assertThrows(IllegalStateException.class, () -> packets.decode(respawn("minecraft:the_end", "minecraft:the_end")));
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

	private static ClientboundLoginPacket login(String type, String level) {
		return new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL, new String[0], registries(),
				type, level, 0, 20, 10, 10, false, true, false, false, null);
	}

	private static ClientboundRespawnPacket respawn(String type, String level) {
		return new ClientboundRespawnPacket(type, level, 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, false,
				false, null);
	}

	private static CompoundTag registries() {
		ListTag types = new ListTag("value");
		types.add(type("minecraft:overworld", 0, -64, 384));
		types.add(type("minecraft:the_nether", 1, 0, 256));
		CompoundTag registry = new CompoundTag("minecraft:dimension_type");
		registry.put(new StringTag("type", "minecraft:dimension_type"));
		registry.put(types);
		CompoundTag registries = new CompoundTag("");
		registries.put(registry);
		return registries;
	}

	private static CompoundTag type(String name, int id, int minY, int height) {
		CompoundTag element = new CompoundTag("element");
		element.put(new IntTag("min_y", minY));
		element.put(new IntTag("height", height));
		CompoundTag entry = new CompoundTag("");
		entry.put(new StringTag("name", name));
		entry.put(new IntTag("id", id));
		entry.put(element);
		return entry;
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
