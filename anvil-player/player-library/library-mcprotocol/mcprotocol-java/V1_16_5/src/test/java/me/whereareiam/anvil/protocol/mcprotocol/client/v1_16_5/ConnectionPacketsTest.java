package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void decodesTheJoinedLevelWithItsTypeNamedByTheLoginsRegistry() {
		assertEquals(ClientboundLogin.builder()
				.entityId(new EntityId(42))
				.level("minecraft:overworld")
				.level("minecraft:the_nether")
				.hardcore(true)
				.dimension("minecraft:overworld")
				.dimensionType(DimensionType.withEffects("minecraft:overworld", "minecraft:overworld", 0, 256))
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
				.build(), packets.decode(new ServerRespawnPacket(nether(), "example:arena", 0, GameMode.SURVIVAL,
				GameMode.SURVIVAL, true, false, false)));
	}

	@Test
	void takesTheOverworldsEffectsForATypeThatDescribesNone() {
		CompoundTag plain = new CompoundTag("");
		plain.put(new ByteTag("ultrawarm", (byte) 0));
		CompoundTag codec = codec(entry("example:plain", 2, plain));

		assertEquals(DimensionType.withEffects("example:plain", "minecraft:overworld", 0, 256),
				((ClientboundLogin) packets.decode(new ServerJoinGamePacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL,
						1, new String[]{"example:plain"}, codec, plain, "example:plain", 0, 20, 10, false, true, false,
						false))).getDimensionType());
	}

	@Test
	void readsTheBlockHeightsATypeOfMinecraft117Names() {
		CompoundTag tall = type("minecraft:overworld", false);
		tall.put(new IntTag("min_y", -32));
		tall.put(new IntTag("height", 320));
		CompoundTag codec = codec(entry("example:tall", 0, tall));
		ServerJoinGamePacket login = new ServerJoinGamePacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL, 1,
				new String[]{"example:tall"}, codec, tall, "example:tall", 0, 20, 10, false, true, false, false);

		assertEquals(DimensionType.withEffects("example:tall", "minecraft:overworld", -32, 320),
				((ClientboundLogin) packets.decode(login)).getDimensionType());
	}

	@Test
	void readsFastLavaFromTheUltrawarmFlagOfTheType() {
		packets.decode(login(overworld()));

		assertTrue(((ClientboundRespawn) packets.decode(new ServerRespawnPacket(nether(), "minecraft:the_nether", 0,
				GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, false))).getDimensionType().isFastLava());
		assertFalse(((ClientboundLogin) packets.decode(login(overworld()))).getDimensionType().isFastLava());
	}

	@Test
	void refusesATypeWithoutItsUltrawarmFlagAsTheVanillaClientCannotReadIt() {
		CompoundTag unflagged = new CompoundTag("");
		unflagged.put(new StringTag("effects", "minecraft:the_nether"));
		CompoundTag codec = codec(entry("example:unflagged", 0, unflagged));
		ServerJoinGamePacket login = new ServerJoinGamePacket(42, false, GameMode.SURVIVAL, GameMode.SURVIVAL, 1,
				new String[]{"example:unflagged"}, codec, unflagged, "example:unflagged", 0, 20, 10, false, true, false,
				false);

		assertThrows(IllegalStateException.class, () -> packets.decode(login));
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

	@Test
	void carriesTheGameModeOfTheLoginAndTheRespawn() {
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.CREATIVE,
				loginMode(GameMode.CREATIVE));
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SPECTATOR,
				respawnMode(GameMode.SPECTATOR));
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.ADVENTURE,
				respawnMode(GameMode.ADVENTURE));
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SURVIVAL,
				respawnMode(GameMode.SURVIVAL));
	}

	@Test
	void takesTheLibrarysUnknownGameModeForSurvival() {
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SURVIVAL,
				loginMode(GameMode.UNKNOWN));
	}

	@Test
	void carriesWhetherTheClientShowsTheDeathScreen() {
		assertTrue(((ClientboundLogin) packets.decode(login(overworld()))).isShowDeathScreen());
		assertFalse(((ClientboundLogin) packets.decode(login(overworld()).withEnableRespawnScreen(false))).isShowDeathScreen());
	}

	private me.whereareiam.anvil.edition.java.protocol.type.player.GameMode loginMode(GameMode mode) {
		return ((ClientboundLogin) packets.decode(login(overworld()).withGameMode(mode))).getGameMode();
	}

	private me.whereareiam.anvil.edition.java.protocol.type.player.GameMode respawnMode(GameMode mode) {
		packets.decode(login(overworld()));
		return ((ClientboundRespawn) packets.decode(new ServerRespawnPacket(overworld(), "minecraft:overworld", 0, mode,
				GameMode.UNKNOWN, false, false, false))).getGameMode();
	}

	private static ServerJoinGamePacket login(CompoundTag dimension) {
		return new ServerJoinGamePacket(42, true, GameMode.SURVIVAL, GameMode.SURVIVAL, 2,
				new String[]{"minecraft:overworld", "minecraft:the_nether"},
				codec(entry("minecraft:overworld", 0, overworld()), entry("minecraft:the_nether", 1, nether())), dimension,
				"minecraft:overworld", 0, 20, 10, false, true, false, true);
	}

	private static CompoundTag overworld() {
		return type("minecraft:overworld", false);
	}

	private static CompoundTag nether() {
		return type("minecraft:the_nether", true);
	}

	private static CompoundTag type(String effects, boolean ultrawarm) {
		CompoundTag type = new CompoundTag("");
		type.put(new StringTag("effects", effects));
		type.put(new ByteTag("ultrawarm", (byte) (ultrawarm ? 1 : 0)));
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
