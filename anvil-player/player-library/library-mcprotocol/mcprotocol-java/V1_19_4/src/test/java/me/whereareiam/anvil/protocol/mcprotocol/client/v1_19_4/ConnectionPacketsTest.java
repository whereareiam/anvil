package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void decodesTheJoinedLevelWithTheEffectsTheLoginsRegistryGivesItsType() {
		assertEquals(ClientboundLogin.builder()
				.entityId(42)
				.level("minecraft:overworld")
				.level("example:arena")
				.dimension("example:arena")
				.dimensionType(DimensionType.withEffects("example:void", "minecraft:the_end"))
				.debug(true)
				.build(), packets.decode(login("example:void")));
	}

	@Test
	void givesTheTypeOfARespawnTheEffectsOfTheLoginsRegistry() {
		packets.decode(login("minecraft:overworld"));

		assertEquals(ClientboundRespawn.builder()
				.dimension("minecraft:the_end")
				.dimensionType(DimensionType.withEffects("example:void", "minecraft:the_end"))
				.flat(true)
				.build(), packets.decode(new ClientboundRespawnPacket("example:void", "minecraft:the_end", 0,
				GameMode.SURVIVAL, GameMode.SURVIVAL, false, true, false, false, null)));
	}

	@Test
	void refusesATypeTheLoginDidNotList() {
		assertThrows(IllegalStateException.class, () -> packets.decode(login("example:unknown")));
	}

	private static ClientboundLoginPacket login(String type) {
		return new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL,
				new String[]{"minecraft:overworld", "example:arena"}, codec(), type, "example:arena", 0, 20, 10, 10, false,
				true, true, false, null);
	}

	/**
	 * The login's registries: the overworld's type and a data pack's type with the end's effects.
	 */
	private static CompoundTag codec() {
		List<Tag> entries = List.of(
				entry("minecraft:overworld", 0, "minecraft:overworld"),
				entry("example:void", 1, "minecraft:the_end")
		);
		CompoundTag registry = new CompoundTag("minecraft:dimension_type");
		registry.put(new ListTag("value", entries));
		CompoundTag codec = new CompoundTag("");
		codec.put(registry);
		return codec;
	}

	private static CompoundTag entry(String name, int id, String effects) {
		CompoundTag element = new CompoundTag("element");
		element.put(new StringTag("effects", effects));
		CompoundTag entry = new CompoundTag("");
		entry.put(new StringTag("name", name));
		entry.put(new IntTag("id", id));
		entry.put(element);
		return entry;
	}
}
