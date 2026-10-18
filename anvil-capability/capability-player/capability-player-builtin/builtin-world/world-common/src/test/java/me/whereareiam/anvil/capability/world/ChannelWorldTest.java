package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import me.whereareiam.anvil.capability.world.model.BlockChange;
import me.whereareiam.anvil.capability.world.model.BlockCoordinates;
import me.whereareiam.anvil.capability.world.model.BlockReading;
import me.whereareiam.anvil.capability.world.model.KnownDimension;
import me.whereareiam.anvil.capability.world.model.LoadedChunks;
import me.whereareiam.anvil.capability.world.model.ReceivedBlock;
import me.whereareiam.anvil.capability.world.model.ReceivedBlocks;
import me.whereareiam.anvil.capability.world.model.SeenBlock;
import me.whereareiam.anvil.capability.world.model.SeenChunk;
import me.whereareiam.anvil.capability.world.model.SeenDimension;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.java.data.BlockRegistry;
import me.whereareiam.anvil.protocol.java.data.JavaBlockData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelWorldTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final BlockRegistry REGISTRY = JavaBlockData.shipped().require(MinecraftVersion.parse("1.21.11"));
	private static final int STONE = 1;
	private static final int GOLD = REGISTRY.block("minecraft:gold_block").orElseThrow().getDefaultStateId();
	private static final String OVERWORLD = "minecraft:overworld";

	@Test
	void readsTheDimensionTheBlocksAndTheChunksFromTheWorker() {
		RecordingChannel channel = new RecordingChannel();
		World world = new ChannelWorld(channel, new BlockNames(REGISTRY));
		assertEquals(Optional.empty(), world.dimension());
		assertEquals(Optional.empty(), world.block(0, 63, 0));

		channel.dimension = new SeenDimension(OVERWORLD, -64, 384);
		channel.blocks.put(new BlockCoordinates(0, 63, 0), STONE);
		channel.chunks = List.of(new SeenChunk(0, 0), new SeenChunk(-1, 0));

		assertEquals(Optional.of(channel.dimension), world.dimension());
		SeenBlock stone = world.block(0, 63, 0).orElseThrow();
		assertEquals("minecraft:stone", stone.name());
		assertEquals(STONE, stone.getStateId());
		assertEquals(Set.copyOf(channel.chunks), world.loadedChunks());
		assertTrue(world.isChunkLoaded(-1, 0));
		assertFalse(world.isChunkLoaded(1, 0));
		assertTrue(world.knowsBlockNames());
		assertEquals(List.of(new BlockCoordinates(0, 63, 0), new BlockCoordinates(0, 63, 0)), channel.reads);
	}

	@Test
	void waitsUntilTheBlockAtAPositionIsKnownAndMatches() {
		RecordingChannel channel = new RecordingChannel();
		World world = new ChannelWorld(channel, new BlockNames(REGISTRY));

		assertEquals("Player did not know the block at 0 63 0 named minecraft:stone", assertThrows(IllegalStateException.class,
				() -> world.awaitBlock(0, 63, 0, BlockQuery.named("stone"), TIMEOUT)).getMessage());

		channel.blocks.put(new BlockCoordinates(0, 63, 0), STONE);
		assertEquals("minecraft:stone", world.awaitBlock(0, 63, 0, BlockQuery.named("stone"), TIMEOUT).name());
		assertEquals("Player did not know the block at 0 63 0 matching", assertThrows(IllegalStateException.class,
				() -> world.awaitBlock(0, 63, 0, block -> false, TIMEOUT)).getMessage());
	}

	@Test
	void findsTheNextUpdateOfAPositionThatMatchesAndContinuesAfterIt() {
		RecordingChannel channel = new RecordingChannel();
		World world = new ChannelWorld(channel, new BlockNames(REGISTRY));
		channel.update(1, block(0, 64, 0, STONE));
		WorldWatch watch = world.watch();

		channel.update(1, block(0, 64, 0, STONE), block(5, 64, 5, GOLD));
		channel.update(1, block(0, 64, 0, GOLD));
		channel.update(2, block(0, 64, 0, GOLD));

		assertEquals(4, watch.unread().size());
		SeenBlock first = watch.changed(0, 64, 0, BlockQuery.named("gold_block"), TIMEOUT);
		assertEquals(GOLD, first.getStateId());
		assertEquals(List.of(new BlockChange(2, OVERWORLD, first)), watch.unread());
		watch.changed(0, 64, 0, BlockQuery.named("gold_block"), TIMEOUT);
		assertEquals("Player did not see the block at 0 64 0 change to named minecraft:gold_block",
				assertThrows(IllegalStateException.class,
						() -> watch.changed(0, 64, 0, BlockQuery.named("gold_block"), TIMEOUT)).getMessage());
		assertTrue(watch.unread().isEmpty());
	}

	@Test
	void expectsABlockToStayUnchangedWithoutMovingTheWatch() {
		RecordingChannel channel = new RecordingChannel();
		World world = new ChannelWorld(channel, new BlockNames(REGISTRY));
		WorldWatch watch = world.watch();

		watch.unchanged(0, 64, 0, TIMEOUT);
		channel.update(1, block(1, 64, 0, GOLD));
		watch.unchanged(0, 64, 0, TIMEOUT);
		channel.update(1, block(0, 64, 0, GOLD));

		assertEquals("Player saw the block at 0 64 0 change, which must stay unchanged: an update set it to "
				+ "minecraft:gold_block at 0 64 0", assertThrows(IllegalStateException.class,
				() -> watch.unchanged(0, 64, 0, TIMEOUT)).getMessage());
		assertEquals(2, watch.unread().size());
	}

	@Test
	void expectsAnUpdateForAwaitFirst() {
		RecordingChannel channel = new RecordingChannel();
		WorldWatch watch = new ChannelWorld(channel, new BlockNames(REGISTRY)).watch();
		Expectation gold = watch.expectChanged(0, 64, 0, BlockQuery.named("gold_block"));

		assertEquals("see the block at 0 64 0 change to named minecraft:gold_block", gold.getDescription());
		assertFalse(gold.met());
		channel.update(1, block(0, 64, 0, GOLD));
		assertTrue(gold.met());
	}

	@Test
	void keepsUpdatesOfAReleaseWithoutBlockDataByTheirStates() {
		RecordingChannel channel = new RecordingChannel();
		World world = new ChannelWorld(channel, new BlockNames(null));
		WorldWatch watch = world.watch();
		channel.update(1, block(0, 64, 0, 7));

		assertFalse(world.knowsBlockNames());
		assertThrows(UnknownBlockNameException.class, () -> watch.changed(0, 64, 0, BlockQuery.named("stone"), TIMEOUT));
		SeenBlock changed = watch.changed(0, 64, 0, BlockQuery.state(7), TIMEOUT);
		assertFalse(changed.isNamed());
		assertEquals(7, changed.getStateId());
	}

	private static ReceivedBlock block(int x, int y, int z, int stateId) {
		return new ReceivedBlock(x, y, z, stateId);
	}

	/**
	 * A worker that answers reads from the test's world and delivers the updates the test publishes at once; a wait
	 * checks its condition once.
	 */
	private static final class RecordingChannel implements ProtocolChannel {
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();
		private final Map<BlockCoordinates, Integer> blocks = new HashMap<>();
		private final List<BlockCoordinates> reads = new ArrayList<>();
		private @Nullable SeenDimension dimension;
		private List<SeenChunk> chunks = List.of();

		private void update(int connection, ReceivedBlock... received) {
			ReceivedBlocks update = new ReceivedBlocks(connection, OVERWORLD, List.of(received));
			listeners.getOrDefault(WorldChannel.CHANGED.getId(), List.of()).forEach(listener -> listener.accept(update));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
			Object response = switch (operation.getId()) {
				case "world.dimension" -> new KnownDimension(dimension);
				case "world.chunks" -> new LoadedChunks(chunks);
				case "world.block" -> read((BlockCoordinates) request);
				default -> throw new AssertionError("Unknown operation " + operation.getId());
			};

			return operation.getResponseType().cast(response);
		}

		private BlockReading read(BlockCoordinates coordinates) {
			reads.add(coordinates);
			Integer state = blocks.get(coordinates);
			return new BlockReading(state != null, state == null ? 0 : state);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(WorldChannel.ID);
		}
	}
}
