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
import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.WorldView;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.BlockChange;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundBlockUpdate;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.edition.java.protocol.packet.world.chunk.ClientboundSectionBlocksUpdate;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.worker.OperationRegistry;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The blocks one player's client knows, read from the world its protocol keeps, which this class starts keeping when
 * the World capability binds, so the chunks of the player's first join are kept too. It answers the host's reads and
 * sends the blocks of each open watch's area that the server updates or sends with a chunk, named by Anvil's block
 * data for the player's protocol.
 */
final class WorldBlocks {
	private final PlayerBindingContext<Object> player;
	private final JavaProtocol protocol;
	private final JavaWorld world;
	private final @Nullable BlockRegistry registry;
	private final Map<Integer, BlockArea> areas = new ConcurrentHashMap<>();

	WorldBlocks(@NotNull PlayerBindingContext<Object> player, @NotNull JavaProtocol protocol) {
		this.player = player;
		this.protocol = protocol;
		this.world = protocol.world();
		this.registry = JavaBlockData.shipped().find(protocol.protocolNumber()).orElse(null);
	}

	/**
	 * Registers the host's reads of blocks and chunks and the opening and closing of watches.
	 */
	void register(@NotNull OperationRegistry operations) {
		operations.register(WorldChannel.BLOCK, position -> world.read(view -> lookup(view, position)));
		operations.register(WorldChannel.CHUNK, chunk -> world.isLoaded(chunk(chunk)));
		operations.register(WorldChannel.CHUNKS, ignored -> new LoadedChunks(world.loadedChunks().stream()
				.map(chunk -> new ChunkPosition(chunk.getX(), chunk.getZ()))
				.toList()));
		operations.register(WorldChannel.WATCH, this::watch);
		operations.register(WorldChannel.UNWATCH, this::unwatch);
	}

	/**
	 * Listens to the packets that change blocks, for the watches; closing it forgets every watch.
	 */
	@NotNull Subscription listen() {
		return Subscription.all(
				protocol.listen(ClientboundBlockUpdate.class, update -> updated(List.of(update.getChange()))),
				protocol.listen(ClientboundSectionBlocksUpdate.class, update -> updated(update.getChanges())),
				protocol.listen(ClientboundLevelChunkWithLight.class, this::loaded),
				areas::clear
		);
	}

	private Void watch(AreaWatch watch) {
		if (areas.putIfAbsent(watch.getId(), watch.getArea()) != null)
			throw new IllegalArgumentException("Watch " + watch.getId() + " is already open");

		return null;
	}

	private Void unwatch(Integer id) {
		areas.remove(id);
		return null;
	}

	/**
	 * Sends the updated blocks of each open area, with the states the packet gave them.
	 */
	private void updated(List<BlockChange> changes) {
		if (areas.isEmpty()) return;

		String level = world.level().map(Level::getKey).orElse(null);
		if (level == null) return;

		for (Map.Entry<Integer, BlockArea> watch : areas.entrySet()) {
			List<BlockReading> blocks = new ArrayList<>();
			for (BlockChange change : changes) {
				BlockPosition position = position(change.getPosition());
				if (watch.getValue().contains(position)) blocks.add(reading(position, change.getBlockState()));
			}
			emit(watch.getKey(), level, blocks);
		}
	}

	/**
	 * Sends the blocks of each open area that a chunk the server sent holds, as the world keeps them now. The blocks
	 * are read under one read of the world and sent after it, so the packet thread never waits for the channel.
	 */
	private void loaded(ClientboundLevelChunkWithLight chunk) {
		if (areas.isEmpty()) return;

		Map<Integer, BlockArea> open = Map.copyOf(areas);
		Loaded loaded = world.read(view -> read(view, chunk.getPosition(), open));
		if (loaded == null) return;

		loaded.blocks().forEach((watch, blocks) -> emit(watch, loaded.level(), blocks));
	}

	private @Nullable Loaded read(
			WorldView view,
			me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition chunk,
			Map<Integer, BlockArea> open
	) {
		Level level = view.level().orElse(null);
		if (level == null || !view.isLoaded(chunk)) return null;

		Map<Integer, List<BlockReading>> blocks = new LinkedHashMap<>();
		for (Map.Entry<Integer, BlockArea> watch : open.entrySet())
			blocks.put(watch.getKey(), column(view, level, new ChunkPosition(chunk.getX(), chunk.getZ()),
					watch.getValue()));

		return new Loaded(level.getKey(), blocks);
	}

	/**
	 * Reads the blocks of an area that lie in one chunk column of a level.
	 */
	private List<BlockReading> column(WorldView view, Level level, ChunkPosition chunk, BlockArea area) {
		int westX = chunk.getX() * ChunkPosition.SIZE;
		int northZ = chunk.getZ() * ChunkPosition.SIZE;
		int fromX = Math.max(area.getMin().getX(), westX);
		int toX = Math.min(area.getMax().getX(), westX + ChunkPosition.SIZE - 1);
		int fromZ = Math.max(area.getMin().getZ(), northZ);
		int toZ = Math.min(area.getMax().getZ(), northZ + ChunkPosition.SIZE - 1);
		int fromY = Math.max(area.getMin().getY(), level.minY());
		int toY = Math.min(area.getMax().getY(), level.minY() + level.sectionCount() * ChunkPosition.SIZE - 1);

		List<BlockReading> blocks = new ArrayList<>();
		for (int y = fromY; y <= toY; y++)
			for (int z = fromZ; z <= toZ; z++)
				for (int x = fromX; x <= toX; x++) {
					OptionalInt state = view.blockState(x, y, z);
					if (state.isPresent()) blocks.add(reading(new BlockPosition(x, y, z), state.getAsInt()));
				}

		return blocks;
	}

	private void emit(int watch, String level, List<BlockReading> blocks) {
		if (!blocks.isEmpty()) player.emit(WorldChannel.CHANGES, new WatchedChanges(watch, level, List.copyOf(blocks)));
	}

	private BlockLookup lookup(WorldView view, BlockPosition position) {
		OptionalInt state = view.blockState(position.getX(), position.getY(), position.getZ());
		return new BlockLookup(state.isPresent() ? reading(position, state.getAsInt()) : null);
	}

	private BlockReading reading(BlockPosition position, int state) {
		return new BlockReading(position, state, identity(state), protocol.protocolNumber());
	}

	/**
	 * Names a state by Anvil's block data, unless it has none for the protocol or the state is not one of vanilla's,
	 * such as a modded server's.
	 */
	private @Nullable BlockIdentity identity(int state) {
		if (registry == null || state < 0 || state >= registry.stateCount()) return null;

		BlockState named = registry.state(state);
		return new BlockIdentity(named.getBlock().getName(), named.getProperties(), named.isAir());
	}

	private static BlockPosition position(me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition position) {
		return new BlockPosition(position.getX(), position.getY(), position.getZ());
	}

	private static me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition chunk(ChunkPosition chunk) {
		return new me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition(chunk.getX(), chunk.getZ());
	}

	/**
	 * The blocks one chunk added to each open area, with the level they are in.
	 */
	private record Loaded(String level, Map<Integer, List<BlockReading>> blocks) {
	}
}
