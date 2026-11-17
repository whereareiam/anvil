package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.model.world.Level;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
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
	private final SynchronizedRegistries registries = new SynchronizedRegistries();
	private final ConnectionLevel level = new ConnectionLevel();
	private final ConnectionPackets packets = new ConnectionPackets(registries, level);

	@Test
	void recordsTheLevelOfEachRespawnForTheConnectionsOtherTranslations() {
		synchronize(new RegistryEntry("minecraft:overworld", null), new RegistryEntry("minecraft:the_nether", null));

		ClientboundRespawn respawn = respawnInto(1);

		assertEquals(Optional.of(new Level("minecraft:overworld", respawn.getDimensionType())), level.current());
	}

	@Test
	void namesTheJoinedLevelsTypeByItsIdInTheRegistryTheConfigurationSynchronized() {
		synchronize(new RegistryEntry("minecraft:overworld", null), new RegistryEntry("example:void",
				described(0, 128).with("effects", new NbtString("minecraft:the_end"))));

		assertEquals(ClientboundLogin.builder()
				.entityId(new EntityId(42))
				.level("minecraft:overworld")
				.level("minecraft:arena")
				.hardcore(true)
				.dimension("minecraft:arena")
				.dimensionType(DimensionType.withEffects("example:void", "minecraft:the_end", 0, 128))
				.flat(true)
				.showDeathScreen(false)
				.build(), packets.decode(new ClientboundLoginPacket(42, true,
				new Key[]{Key.key("minecraft", "overworld"), Key.key("minecraft", "arena")}, 20, 10, 10, false, false, false,
				spawnIn(Key.key("minecraft", "arena"), 1, false, true), false)));
	}

	@Test
	void namesTheGameModeOfEachLoginAndRespawn() {
		synchronize(new RegistryEntry("minecraft:overworld", null));

		ClientboundLogin login = (ClientboundLogin) packets.decode(new ClientboundLoginPacket(42, false,
				new Key[]{Key.key("minecraft", "overworld")}, 20, 10, 10, false, true, false,
				spawnIn(Key.key("minecraft", "overworld"), GameMode.CREATIVE), false));
		ClientboundRespawn respawn = (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(
				spawnIn(Key.key("minecraft", "overworld"), GameMode.SPECTATOR), false, false));
		ClientboundRespawn adventure = (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(
				spawnIn(Key.key("minecraft", "overworld"), GameMode.ADVENTURE), false, false));

		assertEquals(me.whereareiam.anvil.protocol.java.api.type.player.GameMode.CREATIVE, login.getGameMode());
		assertEquals(me.whereareiam.anvil.protocol.java.api.type.player.GameMode.SPECTATOR, respawn.getGameMode());
		assertEquals(me.whereareiam.anvil.protocol.java.api.type.player.GameMode.ADVENTURE, adventure.getGameMode());
		assertEquals(me.whereareiam.anvil.protocol.java.api.type.player.GameMode.SURVIVAL, respawnInto(0).getGameMode());
	}

	@Test
	void takesAVanillaTypeWithoutDescriptionFromTheClientsOwnDataPack() {
		synchronize(new RegistryEntry("minecraft:overworld", null), new RegistryEntry("minecraft:the_nether", null));

		assertEquals(ClientboundRespawn.builder()
				.dimension("minecraft:the_nether")
				.dimensionType(DimensionType.withEffects("minecraft:the_nether", "minecraft:the_nether", 0, 256)
						.toBuilder()
						.fastLava(true)
						.build())
				.debug(true)
				.build(), packets.decode(new ClientboundRespawnPacket(spawnIn(Key.key("minecraft", "the_nether"), 1, true,
				false), false, false)));
		assertEquals(DimensionType.withEffects("minecraft:overworld", "minecraft:overworld", -64, 384), respawnInto(0)
				.getDimensionType());
	}

	@Test
	void takesTheOverworldsEffectsForADescriptionThatNamesNone() {
		synchronize(new RegistryEntry("example:plain", described(-16, 64)));

		assertEquals(DimensionType.withEffects("example:plain", "minecraft:overworld", -16, 64),
				respawnInto(0).getDimensionType());
	}

	@Test
	void leavesTheSkyAndShadingOfAModsEffectsUnknown() {
		synchronize(new RegistryEntry("example:modded",
				described(0, 256).with("effects", new NbtString("example:fog"))));

		assertEquals(DimensionType.builder().key("example:modded").minY(0).height(256).build(),
				respawnInto(0).getDimensionType());
	}

	@Test
	void readsFastLavaFromTheUltrawarmFlagOfADescriptionAndNotFromAnAttributeThisReleaseLacks() {
		NbtCompound attribute = NbtCompound.EMPTY.with("minecraft:gameplay/fast_lava", NbtByte.of(true));
		synchronize(new RegistryEntry("example:hot", described(0, 256).with("ultrawarm", NbtByte.of(true))),
				new RegistryEntry("example:attributed", described(0, 256).with("attributes", attribute)));

		assertTrue(respawnInto(0).getDimensionType().isFastLava());
		assertFalse(respawnInto(1).getDimensionType().isFastLava());
	}

	@Test
	void refusesADescriptionWithoutItsUltrawarmFlag() {
		synchronize(new RegistryEntry("example:unflagged", NbtCompound.EMPTY.with("min_y", new NbtInt(0))
				.with("height", new NbtInt(256))));

		assertThrows(IllegalStateException.class, () -> respawnInto(0));
	}

	@Test
	void refusesATypeTheConfigurationDidNotList() {
		registries.record(new ClientboundRegistryData("minecraft:dialog", List.of(
				new RegistryEntry("example:menu", NbtCompound.EMPTY))));

		assertThrows(IllegalStateException.class, () -> respawnInto(0));
	}

	@Test
	void refusesATypeNoVanillaClientKnowsSentWithoutItsDescription() {
		synchronize(new RegistryEntry("example:modded", null));

		assertThrows(IllegalStateException.class, () -> respawnInto(0));
	}

	@Test
	void refusesADescriptionWithoutBlockHeights() {
		synchronize(new RegistryEntry("example:flat", NbtCompound.EMPTY.with("height", new NbtInt(256))));

		assertThrows(IllegalStateException.class, () -> respawnInto(0));
	}

	@Test
	void decodesReturningToConfigurationAndLeavesOtherPacketsToOtherTranslations() {
		assertEquals(new ClientboundStartConfiguration(), packets.decode(new ClientboundStartConfigurationPacket()));
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryConnectionPacket() {
		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundStartConfiguration.class),
				packets.packets());
	}

	private void synchronize(RegistryEntry... types) {
		registries.record(new ClientboundRegistryData("minecraft:dimension_type", List.of(types)));
	}

	private ClientboundRespawn respawnInto(int type) {
		return (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(spawnIn(Key.key("minecraft", "overworld"),
				type, false, false), false, false));
	}

	private static NbtCompound described(int minY, int height) {
		return NbtCompound.EMPTY.with("min_y", new NbtInt(minY)).with("height", new NbtInt(height))
				.with("ultrawarm", NbtByte.of(false));
	}

	private static PlayerSpawnInfo spawnIn(Key level, int type, boolean debug, boolean flat) {
		return new PlayerSpawnInfo(type, level, 0, GameMode.SURVIVAL, GameMode.SURVIVAL, debug, flat, null, 0);
	}

	private static PlayerSpawnInfo spawnIn(Key level, GameMode gameMode) {
		return new PlayerSpawnInfo(0, level, 0, gameMode, GameMode.SURVIVAL, false, false, null, 0);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
