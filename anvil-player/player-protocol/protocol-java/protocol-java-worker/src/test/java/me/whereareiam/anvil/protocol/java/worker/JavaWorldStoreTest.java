package me.whereareiam.anvil.protocol.java.worker;

import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.model.world.Level;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.BlockChange;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundSectionBlocksUpdate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaWorldStoreTest {
	private static final DimensionType OVERWORLD = DimensionType.known("minecraft:overworld");
	private static final DimensionType NETHER = DimensionType.known("minecraft:the_nether");
	private static final int STONE = 1;
	private static final int DIRT = 10;
	private static final int GLASS = 20;

	private final JavaPacketDispatcher protocol = new JavaPacketDispatcher("test", new SilentConnection());
	private final JavaWorld world = protocol.world();

	@Test
	void knowsNothingBeforeTheLogin() {
		protocol.deliver(chunk(0, 0));

		assertEquals(Optional.empty(), world.level());
		assertFalse(world.isLoaded(new ChunkPosition(0, 0)));
		assertEquals(OptionalInt.empty(), world.blockState(0, 0, 0));
	}

	@Test
	void readsTheBlocksOfALoadedColumnByTheLevelsHeights() {
		protocol.deliver(login("minecraft:overworld", OVERWORLD));
		protocol.deliver(chunk(-1, 2));

		assertEquals(Optional.of(new Level("minecraft:overworld", OVERWORLD)), world.level());
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
	void keepsNoChunksBeforeAnythingAskedForTheWorld() {
		JavaPacketDispatcher unasked = new JavaPacketDispatcher("test", new SilentConnection());
		unasked.deliver(login("minecraft:overworld", OVERWORLD));
		assertFalse(unasked.wants(ClientboundLevelChunkWithLight.class));

		unasked.deliver(chunk(0, 0));
		JavaWorld asked = unasked.world();
		assertTrue(unasked.wants(ClientboundLevelChunkWithLight.class));
		assertEquals(Optional.of(new Level("minecraft:overworld", OVERWORLD)), asked.level());
		assertEquals(Set.of(), asked.loadedChunks());

		unasked.deliver(chunk(0, 0));
		assertEquals(Set.of(new ChunkPosition(0, 0)), asked.loadedChunks());
	}

	@Test
	void appliesTheChunkBeforeItsListenersSeeIt() {
		List<OptionalInt> seen = new ArrayList<>();
		protocol.listen(ClientboundLevelChunkWithLight.class, chunk -> seen.add(world.blockState(0, -64, 0)));
		protocol.deliver(login("minecraft:overworld", OVERWORLD));

		protocol.deliver(chunk(0, 0));

		assertEquals(List.of(OptionalInt.of(STONE)), seen);
	}

	@Test
	void appliesSingleAndSectionBlockUpdates() {
		protocol.deliver(login("minecraft:overworld", OVERWORLD));
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
		protocol.deliver(login("minecraft:overworld", OVERWORLD));
		protocol.deliver(chunk(0, 0));
		protocol.deliver(chunk(1, 0));

		protocol.deliver(new ClientboundForgetLevelChunk(new ChunkPosition(0, 0)));

		assertEquals(Set.of(new ChunkPosition(1, 0)), world.loadedChunks());
		assertEquals(OptionalInt.empty(), world.blockState(0, -64, 0));
	}

	@Test
	void keepsTheColumnsOfALevelTheRespawnKeepsAndForgetsThemInAnother() {
		protocol.deliver(login("minecraft:overworld", OVERWORLD));
		protocol.deliver(chunk(0, 0));

		protocol.deliver(respawn("minecraft:overworld", OVERWORLD));
		assertTrue(world.isLoaded(new ChunkPosition(0, 0)));

		protocol.deliver(respawn("minecraft:the_nether", NETHER));
		assertEquals(Optional.of(new Level("minecraft:the_nether", NETHER)), world.level());
		assertEquals(Set.of(), world.loadedChunks());

		protocol.deliver(chunk(0, 0));
		assertEquals(OptionalInt.of(STONE), world.blockState(0, 0, 0));
		assertEquals(OptionalInt.empty(), world.blockState(0, -1, 0));
	}

	@Test
	void forgetsTheLevelOnALoginAConfigurationAndAClosedConnection() {
		protocol.deliver(login("minecraft:overworld", OVERWORLD));
		protocol.deliver(chunk(0, 0));
		protocol.deliver(login("minecraft:overworld", OVERWORLD));
		assertEquals(Set.of(), world.loadedChunks());

		protocol.deliver(chunk(0, 0));
		protocol.deliver(new ClientboundStartConfiguration());
		assertEquals(Optional.empty(), world.level());
		assertEquals(Set.of(), world.loadedChunks());

		protocol.deliver(login("minecraft:overworld", OVERWORLD));
		protocol.deliver(chunk(0, 0));
		protocol.disconnected();
		assertEquals(Optional.empty(), world.level());
		assertEquals(OptionalInt.empty(), world.blockState(0, -64, 0));
	}

	@Test
	void treatsSectionsAChunkLeavesOutAsAirAndDropsThoseAboveTheLevel() {
		protocol.deliver(login("minecraft:the_nether", NETHER));
		List<LevelChunkSection> sections = new ArrayList<>(Collections.nCopies(20, LevelChunkSection.uniform(DIRT)));
		protocol.deliver(new ClientboundLevelChunkWithLight(new ChunkPosition(0, 0), List.of(LevelChunkSection.uniform(STONE))));
		protocol.deliver(new ClientboundLevelChunkWithLight(new ChunkPosition(1, 0), sections));

		assertEquals(OptionalInt.of(STONE), world.blockState(0, 15, 0));
		assertEquals(OptionalInt.of(LevelChunkSection.AIR), world.blockState(0, 16, 0));
		assertEquals(OptionalInt.of(LevelChunkSection.AIR), world.blockState(0, 255, 0));
		assertEquals(OptionalInt.of(DIRT), world.blockState(16, 255, 0));
		assertEquals(OptionalInt.empty(), world.blockState(16, 256, 0));
	}

	@Test
	void appliesNoPacketWhileAReaderReads() throws Exception {
		protocol.deliver(login("minecraft:overworld", OVERWORLD));
		protocol.deliver(chunk(0, 0));
		CountDownLatch reading = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		CompletableFuture<List<OptionalInt>> reader = CompletableFuture.supplyAsync(() -> world.read(view -> {
			OptionalInt before = view.blockState(0, -64, 0);
			reading.countDown();
			await(release);
			return List.of(before, view.blockState(0, -64, 0));
		}));
		assertTrue(reading.await(5, TimeUnit.SECONDS));
		CompletableFuture<Void> update = CompletableFuture.runAsync(() ->
				protocol.deliver(new ClientboundBlockUpdate(change(0, -64, 0, GLASS))));

		assertThrows(TimeoutException.class, () -> update.get(200, TimeUnit.MILLISECONDS));
		release.countDown();
		assertEquals(List.of(OptionalInt.of(STONE), OptionalInt.of(STONE)), reader.get(5, TimeUnit.SECONDS));
		update.get(5, TimeUnit.SECONDS);
		assertEquals(OptionalInt.of(GLASS), world.blockState(0, -64, 0));
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

	private static ClientboundLogin login(String level, DimensionType type) {
		return ClientboundLogin.builder().entityId(1).level(level).dimension(level).dimensionType(type).build();
	}

	private static ClientboundRespawn respawn(String level, DimensionType type) {
		return ClientboundRespawn.builder().dimension(level).dimensionType(type).build();
	}

	private static BlockChange change(int x, int y, int z, int state) {
		return new BlockChange(BlockPosition.builder().x(x).y(y).z(z).build(), state);
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(5, TimeUnit.SECONDS));
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(interrupted);
		}
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
