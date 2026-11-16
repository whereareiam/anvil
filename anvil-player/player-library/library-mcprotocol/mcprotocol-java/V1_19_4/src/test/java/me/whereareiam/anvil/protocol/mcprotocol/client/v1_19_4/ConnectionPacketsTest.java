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
import me.whereareiam.anvil.protocol.java.api.model.world.Level;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionPacketsTest {
	private final ConnectionLevel level = new ConnectionLevel();
	private final ConnectionPackets packets = new ConnectionPackets(level);

	@Test
	void recordsTheLevelOfEachLoginForTheConnectionsOtherTranslations() {
		ClientboundLogin login = (ClientboundLogin) packets.decode(login("minecraft:overworld"));

		assertEquals(Optional.of(new Level(login.getDimension(), login.getDimensionType())), level.current());
	}

	@Test
	void decodesTheJoinedLevelWithTheEffectsTheLoginsRegistryGivesItsType() {
		assertEquals(ClientboundLogin.builder()
				.entityId(42)
				.level("minecraft:overworld")
				.level("example:arena")
				.dimension("example:arena")
				.dimensionType(DimensionType.withEffects("example:void", "minecraft:the_end", 0, 256))
				.debug(true)
				.build(), packets.decode(login("example:void")));
	}

	@Test
	void givesTheTypeOfARespawnTheEffectsOfTheLoginsRegistry() {
		packets.decode(login("minecraft:overworld"));

		assertEquals(ClientboundRespawn.builder()
				.dimension("minecraft:the_end")
				.dimensionType(DimensionType.withEffects("example:void", "minecraft:the_end", 0, 256))
				.flat(true)
				.build(), packets.decode(new ClientboundRespawnPacket("example:void", "minecraft:the_end", 0,
				GameMode.SURVIVAL, GameMode.SURVIVAL, false, true, false, false, null)));
	}

	@Test
	void refusesATypeTheLoginDidNotList() {
		assertThrows(IllegalStateException.class, () -> packets.decode(login("example:unknown")));
	}

	@Test
	void refusesATypeDescribedWithoutItsBlockHeights() {
		CompoundTag registry = codec().get("minecraft:dimension_type");
		ListTag entries = registry.get("value");
		CompoundTag flat = new CompoundTag("element");
		flat.put(new StringTag("effects", "minecraft:overworld"));
		CompoundTag entry = new CompoundTag("");
		entry.put(new StringTag("name", "example:flat"));
		entry.put(new IntTag("id", 2));
		entry.put(flat);
		entries.add(entry);
		CompoundTag codec = new CompoundTag("");
		codec.put(registry);

		assertThrows(IllegalStateException.class, () -> packets.decode(new ClientboundLoginPacket(42, false,
				GameMode.SURVIVAL, GameMode.SURVIVAL, new String[]{"minecraft:overworld"}, codec, "minecraft:overworld",
				"minecraft:overworld", 0, 20, 10, 10, false, true, true, false, null)));
	}

	@Test
	void carriesWhetherTheClientShowsTheDeathScreen() {
		assertTrue(((ClientboundLogin) packets.decode(login("minecraft:overworld"))).isShowDeathScreen());
		assertFalse(((ClientboundLogin) packets.decode(login("minecraft:overworld").withEnableRespawnScreen(false))).isShowDeathScreen());
	}

	private static ClientboundLoginPacket login(String type) {
		return new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL,
				new String[]{"minecraft:overworld", "example:arena"}, codec(), type, "example:arena", 0, 20, 10, 10, false,
				true, true, false, null);
	}

	/**
	 * The login's registries: the overworld's type and a data pack's type with the end's effects and heights.
	 */
	private static CompoundTag codec() {
		List<Tag> entries = new ArrayList<>(List.of(
				entry("minecraft:overworld", 0, "minecraft:overworld", -64, 384),
				entry("example:void", 1, "minecraft:the_end", 0, 256)
		));
		CompoundTag registry = new CompoundTag("minecraft:dimension_type");
		registry.put(new ListTag("value", entries));
		CompoundTag codec = new CompoundTag("");
		codec.put(registry);
		return codec;
	}

	private static CompoundTag entry(String name, int id, String effects, int minY, int height) {
		CompoundTag element = new CompoundTag("element");
		element.put(new StringTag("effects", effects));
		element.put(new IntTag("min_y", minY));
		element.put(new IntTag("height", height));
		CompoundTag entry = new CompoundTag("");
		entry.put(new StringTag("name", name));
		entry.put(new IntTag("id", id));
		entry.put(element);
		return entry;
	}
}
