package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
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
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionPacketsTest {
	private final ConnectionLevel level = new ConnectionLevel();
	private final ConnectionPackets packets = new ConnectionPackets(level);

	@Test
	void recordsTheLevelOfEachLoginForTheConnectionsOtherTranslations() {
		ClientboundLogin login = (ClientboundLogin) packets.decode(login(overworld()));

		assertEquals(Optional.of(new Level(login.getDimension(), login.getDimensionType())), level.current());
	}

	@Test
	void decodesTheJoinedLevelWithItsTypeNamedByTheLoginsRegistry() {
		assertEquals(ClientboundLogin.builder()
				.entityId(new EntityId(42))
				.level("minecraft:overworld")
				.level("minecraft:the_nether")
				.hardcore(true)
				.dimension("minecraft:overworld")
				.dimensionType(DimensionType.withEffects("minecraft:overworld", "minecraft:overworld", -64, 384))
				.flat(true)
				.build(), packets.decode(login(overworld())));
	}

	@Test
	void namesTheTypeOfARespawnByTheRegistryOfTheLogin() {
		packets.decode(login(overworld()));

		assertEquals(ClientboundRespawn.builder()
				.dimension("example:arena")
				.dimensionType(DimensionType.withEffects("minecraft:the_nether", "minecraft:the_nether", 0, 256)
						.toBuilder()
						.fastLava(true)
						.build())
				.debug(true)
				.build(), packets.decode(new ClientboundRespawnPacket(nether(), "example:arena", 0, GameMode.SURVIVAL,
				GameMode.SURVIVAL, true, false, false)));
	}

	@Test
	void takesTheOverworldsEffectsForATypeThatDescribesNone() {
		CompoundTag plain = heights(new CompoundTag(""), 0, 256);
		plain.put(new ByteTag("ultrawarm", (byte) 0));
		CompoundTag codec = codec(entry("example:plain", 2, plain));

		assertEquals(DimensionType.withEffects("example:plain", "minecraft:overworld", 0, 256),
				((ClientboundLogin) packets.decode(new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL,
						1, new String[]{"example:plain"}, codec, plain, "example:plain", 0, 20, 10, 10, false, true, false,
						false))).getDimensionType());
	}

	@Test
	void decodesTheGameModeOfEachLoginAndRespawnWithoutItsUnknownPreviousMode() {
		ClientboundLogin login = (ClientboundLogin) packets.decode(login(overworld()).withGameMode(GameMode.CREATIVE)
				.withPreviousGamemode(GameMode.UNKNOWN));
		ClientboundRespawn respawn = (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(nether(),
				"minecraft:the_nether", 0, GameMode.SPECTATOR, GameMode.UNKNOWN, false, false, false));

		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.CREATIVE, login.getGameMode());
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SPECTATOR, respawn.getGameMode());
	}

	@Test
	void readsFastLavaFromTheUltrawarmFlagOfTheType() {
		assertFalse(((ClientboundLogin) packets.decode(login(overworld()))).getDimensionType().isFastLava());
		assertTrue(((ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(nether(), "minecraft:the_nether",
				0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, false))).getDimensionType().isFastLava());
	}

	@Test
	void refusesATypeWithoutItsUltrawarmFlagAsTheVanillaClientCannotReadIt() {
		CompoundTag unflagged = heights(new CompoundTag(""), 0, 256);
		CompoundTag codec = codec(entry("example:unflagged", 0, unflagged));
		ClientboundLoginPacket login = new ClientboundLoginPacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL, 1,
				new String[]{"example:unflagged"}, codec, unflagged, "example:unflagged", 0, 20, 10, 10, false, true,
				false, false);

		assertThrows(IllegalStateException.class, () -> packets.decode(login));
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

	@Test
	void carriesWhetherTheClientShowsTheDeathScreen() {
		assertTrue(((ClientboundLogin) packets.decode(login(overworld()))).isShowDeathScreen());
		assertFalse(((ClientboundLogin) packets.decode(login(overworld()).withEnableRespawnScreen(false))).isShowDeathScreen());
	}

	private static ClientboundLoginPacket login(CompoundTag dimension) {
		return new ClientboundLoginPacket(42, true, GameMode.SURVIVAL, GameMode.SURVIVAL, 2,
				new String[]{"minecraft:overworld", "minecraft:the_nether"},
				codec(entry("minecraft:overworld", 0, overworld()), entry("minecraft:the_nether", 1, nether())), dimension,
				"minecraft:overworld", 0, 20, 10, 10, false, true, false, true);
	}

	private static CompoundTag overworld() {
		return heights(type("minecraft:overworld", false), -64, 384);
	}

	private static CompoundTag nether() {
		return heights(type("minecraft:the_nether", true), 0, 256);
	}

	private static CompoundTag type(String effects, boolean ultrawarm) {
		CompoundTag type = new CompoundTag("");
		type.put(new StringTag("effects", effects));
		type.put(new ByteTag("ultrawarm", (byte) (ultrawarm ? 1 : 0)));
		return type;
	}

	private static CompoundTag heights(CompoundTag type, int minY, int height) {
		type.put(new IntTag("min_y", minY));
		type.put(new IntTag("height", height));
		return type;
	}

	private static CompoundTag entry(String name, int id, CompoundTag type) {
		CompoundTag element = new CompoundTag("element", type.getValue());
		CompoundTag entry = new CompoundTag("");
		entry.put(new StringTag("name", name));
		entry.put(new IntTag("id", id));
		entry.put(element);
		return entry;
	}

	private static CompoundTag codec(CompoundTag... entries) {
		CompoundTag registry = new CompoundTag("minecraft:dimension_type");
		registry.put(new StringTag("type", "minecraft:dimension_type"));
		registry.put(new ListTag("value", List.<Tag>of(entries)));
		CompoundTag codec = new CompoundTag("");
		codec.put(registry);
		return codec;
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
