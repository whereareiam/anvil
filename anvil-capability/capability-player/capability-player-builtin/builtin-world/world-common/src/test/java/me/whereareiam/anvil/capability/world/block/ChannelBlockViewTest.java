package me.whereareiam.anvil.capability.world.block;

import me.whereareiam.anvil.capability.world.channel.AreaWatch;
import me.whereareiam.anvil.capability.world.channel.BlockLookup;
import me.whereareiam.anvil.capability.world.channel.BlockReading;
import me.whereareiam.anvil.capability.world.channel.LoadedChunks;
import me.whereareiam.anvil.capability.world.channel.WatchedChanges;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.block.Block;
import me.whereareiam.anvil.capability.world.model.block.BlockArea;
import me.whereareiam.anvil.capability.world.model.block.BlockIdentity;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.capability.world.model.level.ChunkPosition;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelBlockViewTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final BlockPosition DOOR = new BlockPosition(12, 64, -3);
	private static final BlockIdentity CLOSED = new BlockIdentity("minecraft:oak_door", Map.of("open", "false"), false);
	private static final BlockIdentity OPEN = new BlockIdentity("minecraft:oak_door", Map.of("open", "true"), false);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(WorldChannel.ID));
	private final List<AreaWatch> opened = new ArrayList<>();
	private final List<Integer> closed = new ArrayList<>();
	private final ChannelBlockView blocks = new ChannelBlockView(channel);

	ChannelBlockViewTest() {
		channel.answer(WorldChannel.WATCH, watch -> {
			opened.add(watch);
			return null;
		});
		channel.answer(WorldChannel.UNWATCH, id -> {
			closed.add(id);
			return null;
		});
	}

	@Test
	void readsABlockTheWorkerKnowsAndNothingOfAChunkItLacks() {
		channel.answer(WorldChannel.BLOCK, position -> new BlockLookup(position.equals(DOOR) ? reading(1234, CLOSED) : null));

		Block door = blocks.block(DOOR).orElseThrow();

		assertEquals(new Block(DOOR, 1234, CLOSED, 774), door);
		assertEquals("minecraft:oak_door", door.name());
		assertEquals(Optional.empty(), blocks.block(new BlockPosition(1000, 64, 1000)));
	}

	@Test
	void asksTheWorkerForOneChunkAndForAllOfThem() {
		channel.answer(WorldChannel.CHUNK, chunk -> chunk.equals(new ChunkPosition(0, -1)));
		channel.answer(WorldChannel.CHUNKS, ignored -> new LoadedChunks(List.of(new ChunkPosition(0, -1))));

		assertTrue(blocks.isLoaded(new ChunkPosition(0, -1)));
		assertFalse(blocks.isLoaded(new ChunkPosition(5, 5)));
		assertEquals(Set.of(new ChunkPosition(0, -1)), blocks.loadedChunks());
		assertEquals(List.of("world.chunk", "world.chunk", "world.chunks"), channel.requests());
	}

	@Test
	void returnsABlockThatAlreadyMatchesAndClosesItsWatch() {
		channel.answer(WorldChannel.BLOCK, position -> new BlockLookup(reading(1234, CLOSED)));

		Block door = blocks.until(DOOR, BlockQuery.named("oak_door"));

		assertEquals(1234, door.getState());
		assertEquals(List.of(new AreaWatch(1, BlockArea.of(DOOR))), opened);
		assertEquals(List.of(1), closed);
	}

	@Test
	void waitsForTheBlockToChangeWhenItDoesNotMatchYet() {
		channel.answer(WorldChannel.BLOCK, position -> new BlockLookup(reading(1234, CLOSED)));
		channel.meanwhile(() -> channel.publish(WorldChannel.CHANGES,
				new WatchedChanges(1, OVERWORLD, List.of(reading(1235, OPEN)))));

		Block door = blocks.until(DOOR, BlockQuery.withProperty("open", "true"));

		assertEquals(1235, door.getState());
		assertEquals(List.of(1), closed);
	}

	@Test
	void waitsForABlockOfAChunkTheClientDoesNotHaveYet() {
		channel.answer(WorldChannel.BLOCK, position -> new BlockLookup(null));
		channel.meanwhile(() -> channel.publish(WorldChannel.CHANGES,
				new WatchedChanges(1, OVERWORLD, List.of(reading(1234, CLOSED)))));

		assertEquals(1234, blocks.until(DOOR, BlockQuery.any()).getState());
	}

	@Test
	void closesTheWatchOfAWaitThatTimesOut() {
		channel.answer(WorldChannel.BLOCK, position -> new BlockLookup(reading(1234, CLOSED)));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> blocks.until(DOOR, BlockQuery.withProperty("open", "true"), Duration.ofMillis(10)));

		assertTrue(failure.getMessage().contains("with open=true"), failure.getMessage());
		assertEquals(List.of(1), closed);
	}

	@Test
	void forgetsAWatchTheWorkerRefuses() {
		channel.answer(WorldChannel.WATCH, watch -> {
			throw new IllegalStateException("refused");
		});

		assertThrows(IllegalStateException.class, () -> blocks.watch(BlockArea.of(DOOR)));
		channel.publish(WorldChannel.CHANGES, new WatchedChanges(1, OVERWORLD, List.of(reading(1234, CLOSED))));

		assertEquals(List.of(), closed);
	}

	private static BlockReading reading(int state, BlockIdentity identity) {
		return new BlockReading(DOOR, state, identity, 774);
	}
}
