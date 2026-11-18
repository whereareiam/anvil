package me.whereareiam.anvil.capability.world.java;

import me.whereareiam.anvil.capability.world.channel.AreaWatch;
import me.whereareiam.anvil.capability.world.channel.BlockLookup;
import me.whereareiam.anvil.capability.world.channel.BlockReading;
import me.whereareiam.anvil.capability.world.channel.LoadedChunks;
import me.whereareiam.anvil.capability.world.channel.WatchedChanges;
import me.whereareiam.anvil.capability.world.channel.WorldChannel;
import me.whereareiam.anvil.capability.world.model.block.BlockArea;
import me.whereareiam.anvil.capability.world.model.block.BlockIdentity;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.capability.world.model.level.ChunkPosition;
import me.whereareiam.anvil.edition.java.data.block.BlockRegistry;
import me.whereareiam.anvil.edition.java.data.block.JavaBlockData;
import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.BlockChange;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundForgetLevelChunk;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundSectionBlocksUpdate;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.worker.TestOperations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldBlocksTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final BlockRegistry BLOCKS = JavaBlockData.shipped().require(TestJavaPlayer.PROTOCOL);
	private static final int STONE = BLOCKS.block("minecraft:stone").orElseThrow().getDefaultStateId();
	private static final int GLASS = BLOCKS.block("minecraft:glass").orElseThrow().getDefaultStateId();
	private static final BlockPosition IN_CHUNK = new BlockPosition(3, -64, 7);

	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();
	private final WorldBlocks blocks = new WorldBlocks(player, player.protocol(JavaProtocol.class));
	private final Subscription listening = blocks.listen();

	WorldBlocksTest() {
		blocks.register(operations);
		player.connect();
		player.deliver(ClientboundLogin.builder()
				.entityId(new EntityId(1))
				.level(OVERWORLD)
				.dimension(OVERWORLD)
				.dimensionType(DimensionType.known(OVERWORLD))
				.build());
	}

	@Test
	void keepsTheChunksOfTheFirstJoinBecauseTheWorldStartsKeepingAtBind() {
		player.deliver(stoneFloor(0, 0));

		assertEquals(new BlockLookup(reading(IN_CHUNK, STONE)), operations.call(WorldChannel.BLOCK, IN_CHUNK));
		assertEquals(Boolean.TRUE, operations.call(WorldChannel.CHUNK, new ChunkPosition(0, 0)));
		assertEquals(new LoadedChunks(List.of(new ChunkPosition(0, 0))), operations.call(WorldChannel.CHUNKS, null));
	}

	@Test
	void readsNothingOfAChunkTheClientLacks() {
		assertEquals(new BlockLookup(null), operations.call(WorldChannel.BLOCK, IN_CHUNK));
		assertEquals(Boolean.FALSE, operations.call(WorldChannel.CHUNK, new ChunkPosition(0, 0)));

		player.deliver(stoneFloor(0, 0));
		player.deliver(new ClientboundForgetLevelChunk(chunk(0, 0)));

		assertEquals(new BlockLookup(null), operations.call(WorldChannel.BLOCK, IN_CHUNK));
	}

	@Test
	void namesEachStateByTheBlockDataOfThePlayersProtocol() {
		player.deliver(stoneFloor(0, 0));

		BlockReading stone = operations.call(WorldChannel.BLOCK, IN_CHUNK).getReading();

		assertEquals(new BlockIdentity("minecraft:stone", Map.of(), false), stone.getIdentity());
		assertEquals(TestJavaPlayer.PROTOCOL, stone.getProtocol());
	}

	@Test
	void leavesAStateOutsideVanillasRegistryUnnamed() {
		player.deliver(new ClientboundBlockUpdate(change(IN_CHUNK, BLOCKS.stateCount())));
		player.deliver(stoneFloor(0, 0));
		player.deliver(new ClientboundBlockUpdate(change(IN_CHUNK, BLOCKS.stateCount())));

		assertNull(operations.call(WorldChannel.BLOCK, IN_CHUNK).getReading().getIdentity());
	}

	@Test
	void sendsTheUpdatesOfAWatchedAreaOnly() {
		player.deliver(stoneFloor(0, 0));
		BlockPosition outside = new BlockPosition(4, -64, 7);
		operations.call(WorldChannel.WATCH, new AreaWatch(1, BlockArea.of(IN_CHUNK)));

		player.deliver(new ClientboundSectionBlocksUpdate(List.of(change(outside, GLASS), change(IN_CHUNK, GLASS))));
		player.deliver(new ClientboundBlockUpdate(change(outside, STONE)));

		assertEquals(List.of(new WatchedChanges(1, OVERWORLD, List.of(reading(IN_CHUNK, GLASS)))),
				player.emitted(WorldChannel.CHANGES));
	}

	@Test
	void sendsTheWatchedBlocksOfAChunkTheServerSends() {
		BlockArea area = BlockArea.of(new BlockPosition(15, -64, 0), new BlockPosition(16, -64, 0));
		operations.call(WorldChannel.WATCH, new AreaWatch(4, area));

		player.deliver(stoneFloor(1, 0));

		assertEquals(List.of(new WatchedChanges(4, OVERWORLD, List.of(reading(new BlockPosition(16, -64, 0), STONE)))),
				player.emitted(WorldChannel.CHANGES));
	}

	@Test
	void stopsSendingAClosedWatchAndEveryWatchWhenTheBindingCloses() {
		player.deliver(stoneFloor(0, 0));
		operations.call(WorldChannel.WATCH, new AreaWatch(1, BlockArea.of(IN_CHUNK)));
		operations.call(WorldChannel.WATCH, new AreaWatch(2, BlockArea.of(IN_CHUNK)));

		operations.call(WorldChannel.UNWATCH, 1);
		player.deliver(new ClientboundBlockUpdate(change(IN_CHUNK, GLASS)));
		listening.close();
		player.deliver(new ClientboundBlockUpdate(change(IN_CHUNK, STONE)));

		assertEquals(List.of(new WatchedChanges(2, OVERWORLD, List.of(reading(IN_CHUNK, GLASS)))),
				player.emitted(WorldChannel.CHANGES));
	}

	private static ClientboundLevelChunkWithLight stoneFloor(int x, int z) {
		List<LevelChunkSection> sections = new ArrayList<>(Collections.nCopies(24, LevelChunkSection.EMPTY));
		sections.set(0, LevelChunkSection.uniform(STONE));

		return new ClientboundLevelChunkWithLight(chunk(x, z), List.copyOf(sections));
	}

	private static me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition chunk(int x, int z) {
		return new me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition(x, z);
	}

	private static BlockChange change(BlockPosition position, int state) {
		return new BlockChange(me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition.builder()
				.x(position.getX())
				.y(position.getY())
				.z(position.getZ())
				.build(), state);
	}

	private static BlockReading reading(BlockPosition position, int state) {
		BlockState named = BLOCKS.state(state);
		return new BlockReading(position, state, new BlockIdentity(named.getBlock().getName(), named.getProperties(),
				named.isAir()), TestJavaPlayer.PROTOCOL);
	}
}
