package me.whereareiam.anvil.protocol.java.worker;

import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.model.world.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSectionBlocksUpdate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaChunkStoreTest {
	private static final Dimension OVERWORLD = new Dimension("minecraft:overworld", -64, 384);
	private static final Dimension NETHER = new Dimension("minecraft:the_nether", 0, 256);
	private static final int STONE = 1;
	private static final int DIRT = 10;
	private static final int GLASS = 20;

	private final JavaPacketDispatcher protocol = new JavaPacketDispatcher("test", new SilentConnection());
	private final JavaWorld world = protocol.world();

	@Test
	void knowsNothingBeforeTheLogin() {
		protocol.deliver(chunk(0, 0));

		assertEquals(Optional.empty(), world.dimension());
		assertFalse(world.isLoaded(new ChunkPosition(0, 0)));
		assertEquals(OptionalInt.empty(), world.blockState(0, 0, 0));
	}

	@Test
	void readsTheBlocksOfALoadedColumnByTheLevelsHeights() {
		protocol.deliver(new ClientboundLogin(1, OVERWORLD));
		protocol.deliver(chunk(-1, 2));

		assertEquals(Optional.of(OVERWORLD), world.dimension());
		assertTrue(world.isLoaded(new ChunkPosition(-1, 2)));
		assertEquals(Set.of(new ChunkPosition(-1, 2)), world.loadedChunks());
		assertEquals(OptionalInt.of(STONE), world.blockState(-16, -64, 32));
		assertEquals(OptionalInt.of(STONE), world.blockState(-1, -49, 47));
		assertEquals(OptionalInt.of(DIRT), world.blockState(-5, -48, 40));
		assertEquals(OptionalInt.of(LevelChunkSection.AIR), world.blockState(-5, 100, 40));
		assertEquals(OptionalInt.empty(), world.blockState(-5, -65, 40));
		assertEquals(OptionalInt.empty(), world.blockState(-5, 320, 40));
		assertEquals(OptionalInt.empty(), world.blockState(0, -64, 32));
	}

	@Test
	void appliesTheChunkBeforeItsListenersSeeIt() {
		List<OptionalInt> seen = new ArrayList<>();
		protocol.listen(ClientboundLevelChunkWithLight.class, chunk -> seen.add(world.blockState(0, -64, 0)));
		protocol.deliver(new ClientboundLogin(1, OVERWORLD));

		protocol.deliver(chunk(0, 0));

		assertEquals(List.of(OptionalInt.of(STONE)), seen);
	}

	@Test
	void appliesSingleAndSectionBlockUpdates() {
		protocol.deliver(new ClientboundLogin(1, OVERWORLD));
		protocol.deliver(chunk(0, 0));

		protocol.deliver(new ClientboundBlockUpdate(change(3, -60, 4, GLASS)));
		protocol.deliver(new ClientboundSectionBlocksUpdate(List.of(change(0, 70, 0, STONE), change(1, 70, 0, DIRT))));
		protocol.deliver(new ClientboundBlockUpdate(change(40, -60, 4, GLASS)));

		assertEquals(OptionalInt.of(GLASS), world.blockState(3, -60, 4));
		assertEquals(OptionalInt.of(STONE), world.blockState(4, -60, 4));
		assertEquals(OptionalInt.of(STONE), world.blockState(0, 70, 0));
		assertEquals(OptionalInt.of(DIRT), world.blockState(1, 70, 0));
		assertEquals(OptionalInt.of(LevelChunkSection.AIR), world.blockState(2, 70, 0));
		assertEquals(OptionalInt.empty(), world.blockState(40, -60, 4));
	}

	@Test
	void forgetsAColumnTheServerForgets() {
		protocol.deliver(new ClientboundLogin(1, OVERWORLD));
		protocol.deliver(chunk(0, 0));
		protocol.deliver(chunk(1, 0));

		protocol.deliver(new ClientboundForgetLevelChunk(new ChunkPosition(0, 0)));

		assertEquals(Set.of(new ChunkPosition(1, 0)), world.loadedChunks());
		assertEquals(OptionalInt.empty(), world.blockState(0, -64, 0));
	}

	@Test
	void keepsTheColumnsOfALevelTheRespawnKeepsAndForgetsThemInAnother() {
		protocol.deliver(new ClientboundLogin(1, OVERWORLD));
		protocol.deliver(chunk(0, 0));

		protocol.deliver(new ClientboundRespawn(OVERWORLD));
		assertTrue(world.isLoaded(new ChunkPosition(0, 0)));

		protocol.deliver(new ClientboundRespawn(NETHER));
		assertEquals(Optional.of(NETHER), world.dimension());
		assertEquals(Set.of(), world.loadedChunks());

		protocol.deliver(chunk(0, 0));
		assertEquals(OptionalInt.of(STONE), world.blockState(0, 0, 0));
		assertEquals(OptionalInt.empty(), world.blockState(0, -1, 0));
	}

	@Test
	void forgetsTheLevelOnALoginAConfigurationAndAClosedConnection() {
		protocol.deliver(new ClientboundLogin(1, OVERWORLD));
		protocol.deliver(chunk(0, 0));
		protocol.deliver(new ClientboundLogin(2, OVERWORLD));
		assertEquals(Set.of(), world.loadedChunks());

		protocol.deliver(chunk(0, 0));
		protocol.deliver(new ClientboundStartConfiguration());
		assertEquals(Optional.empty(), world.dimension());
		assertEquals(Set.of(), world.loadedChunks());

		protocol.deliver(new ClientboundLogin(3, OVERWORLD));
		protocol.deliver(chunk(0, 0));
		protocol.disconnected();
		assertEquals(Optional.empty(), world.dimension());
		assertEquals(OptionalInt.empty(), world.blockState(0, -64, 0));
	}

	@Test
	void treatsSectionsAChunkLeavesOutAsAir() {
		protocol.deliver(new ClientboundLogin(1, NETHER));
		protocol.deliver(new ClientboundLevelChunkWithLight(new ChunkPosition(0, 0),
				List.of(LevelChunkSection.uniform(STONE))));

		assertEquals(OptionalInt.of(STONE), world.blockState(0, 15, 0));
		assertEquals(OptionalInt.of(LevelChunkSection.AIR), world.blockState(0, 16, 0));
		assertEquals(OptionalInt.of(LevelChunkSection.AIR), world.blockState(0, 255, 0));
	}

	/**
	 * A column whose lowest section is stone and whose second is dirt, above which there is only air.
	 */
	private static ClientboundLevelChunkWithLight chunk(int x, int z) {
		List<LevelChunkSection> sections = new ArrayList<>(Collections.nCopies(24, LevelChunkSection.EMPTY));
		sections.set(0, LevelChunkSection.uniform(STONE));
		sections.set(1, LevelChunkSection.uniform(DIRT));

		return new ClientboundLevelChunkWithLight(new ChunkPosition(x, z), List.copyOf(sections));
	}

	private static BlockChange change(int x, int y, int z, int state) {
		return new BlockChange(BlockPosition.builder().x(x).y(y).z(z).build(), state);
	}

	private static final class SilentConnection implements JavaConnection {
		@Override
		public void send(@NotNull ServerboundPacket packet) {
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return true;
		}
	}
}
