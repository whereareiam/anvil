package me.whereareiam.anvil.player.library.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.ConnectionLevel;
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

		assertEquals(Optional.of(new Level(login.dimension(), login.dimensionType())), level.current());
	}

	@Test
	void decodesTheJoinedLevelWithTheEffectsTheLoginsRegistryGivesItsType() {
		assertEquals(ClientboundLogin.builder()
				.entityId(new EntityId(42))
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
	void decodesTheGameModeOfEachLoginAndRespawn() {
		ClientboundLogin login = (ClientboundLogin) packets.decode(login("minecraft:overworld")
				.withGameMode(GameMode.ADVENTURE).withPreviousGamemode(null));
		ClientboundRespawn respawn = (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket("example:void",
				"minecraft:the_end", 0, GameMode.CREATIVE, null, false, true, false, false, null));

		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.ADVENTURE, login.gameMode());
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.CREATIVE, respawn.gameMode());
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
	void readsFastLavaFromTheUltrawarmFlagOfEachTypeOfTheLoginsRegistry() {
		assertFalse(((ClientboundLogin) packets.decode(login("example:void"))).dimensionType().fastLava());
		assertTrue(((ClientboundLogin) packets.decode(login("example:hot"))).dimensionType().fastLava());
	}

	@Test
	void refusesATypeDescribedWithoutItsUltrawarmFlag() {
		CompoundTag registry = codec().get("minecraft:dimension_type");
		ListTag entries = registry.get("value");
		CompoundTag unflagged = entry("example:unflagged", 3, "minecraft:overworld", 0, 256, false);
		CompoundTag element = unflagged.get("element");
		element.remove("ultrawarm");
		entries.add(unflagged);
		CompoundTag codec = new CompoundTag("");
		codec.put(registry);

		assertThrows(IllegalStateException.class, () -> packets.decode(new ClientboundLoginPacket(42, false,
				GameMode.SURVIVAL, GameMode.SURVIVAL, new String[]{"minecraft:overworld"}, codec, "minecraft:overworld",
				"minecraft:overworld", 0, 20, 10, 10, false, true, true, false, null)));
	}

	@Test
	void carriesWhetherTheClientShowsTheDeathScreen() {
		assertTrue(((ClientboundLogin) packets.decode(login("minecraft:overworld"))).showDeathScreen());
		assertFalse(((ClientboundLogin) packets.decode(login("minecraft:overworld").withEnableRespawnScreen(false))).showDeathScreen());
	}

	private static ClientboundLoginPacket login(String type) {
		return new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL,
				new String[]{"minecraft:overworld", "example:arena"}, codec(), type, "example:arena", 0, 20, 10, 10, false,
				true, true, false, null);
	}

	/**
	 * The login's registries: the overworld's type, a data pack's type with the end's effects and heights, and an
	 * ultrawarm type of a data pack.
	 */
	private static CompoundTag codec() {
		List<Tag> entries = new ArrayList<>(List.of(
				entry("minecraft:overworld", 0, "minecraft:overworld", -64, 384, false),
				entry("example:void", 1, "minecraft:the_end", 0, 256, false),
				entry("example:hot", 2, "minecraft:the_nether", 0, 256, true)
		));
		CompoundTag registry = new CompoundTag("minecraft:dimension_type");
		registry.put(new ListTag("value", entries));
		CompoundTag codec = new CompoundTag("");
		codec.put(registry);
		return codec;
	}

	private static CompoundTag entry(String name, int id, String effects, int minY, int height, boolean ultrawarm) {
		CompoundTag element = new CompoundTag("element");
		element.put(new StringTag("effects", effects));
		element.put(new IntTag("min_y", minY));
		element.put(new IntTag("height", height));
		element.put(new ByteTag("ultrawarm", (byte) (ultrawarm ? 1 : 0)));
		CompoundTag entry = new CompoundTag("");
		entry.put(new StringTag("name", name));
		entry.put(new IntTag("id", id));
		entry.put(element);
		return entry;
	}
}
