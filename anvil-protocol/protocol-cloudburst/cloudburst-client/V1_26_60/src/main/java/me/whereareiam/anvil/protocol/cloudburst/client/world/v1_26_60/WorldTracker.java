package me.whereareiam.anvil.protocol.cloudburst.client.world.v1_26_60;

import io.netty.buffer.ByteBuf;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry;
import org.cloudburstmc.protocol.bedrock.data.SubChunkData;
import org.cloudburstmc.protocol.bedrock.data.definitions.DimensionDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkPacket;
import org.cloudburstmc.protocol.bedrock.packet.SubChunkRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * The blocks of the world around one Bedrock connection, kept as a client keeps them so that its interactions can
 * name the block they target. It reads the chunks of level chunk packets, requests the sub-chunks of chunks the
 * server sends in sub-chunk request mode and reads the responses, and applies the first-layer block updates of
 * single blocks and of sub-chunks.
 *
 * <p>It holds only the dimension the player is in: the game start and every dimension change start an empty world
 * of that dimension, whose height comes from the dimension data the server sent or else from the vanilla
 * dimensions. A chunk sent in sub-chunk request mode is requested from the dimension's lowest sub-chunk on, as many
 * as the server's limit names or else the whole height. A chunk leaves the world when the server's chunk publisher or
 * chunk radius no longer covers it; the check allows the chunks a server loads around the player's own chunk, whose
 * centers lie up to about two and a half chunks further out than the radius.</p>
 *
 * <p>The connection's network thread hands it every received packet, before the client's own packet handler sees
 * the packet; players read blocks from other threads. A packet it cannot read drops what it described and is
 * reported on standard error, which the worker's diagnostics show, without affecting the connection.</p>
 */
public final class WorldTracker implements BedrockPacketHandler {
	/**
	 * Codecs from this protocol on name the dimension of each level chunk.
	 */
	private static final int CHUNK_DIMENSION_PROTOCOL = 649;
	private static final int OVERWORLD = 0;
	private static final int NETHER = 1;
	private static final int END = 2;
	private static final Map<String, Integer> DIMENSION_IDS = Map.of(
			"minecraft:overworld", OVERWORLD,
			"minecraft:nether", NETHER,
			"minecraft:the_end", END
	);
	/**
	 * Distance in blocks beyond the publisher radius that still covers a chunk: a server loads the chunks around the
	 * player's chunk rather than its block, which puts their centers up to two and a half chunks further out.
	 */
	private static final int RADIUS_MARGIN = 48;

	private final int protocol;
	private final Consumer<BedrockPacket> sender;
	private final Map<Integer, Height> heights = new ConcurrentHashMap<>(Map.of(
			OVERWORLD, new Height(-64, 320),
			NETHER, new Height(0, 128),
			END, new Height(0, 256)
	));

	private volatile @Nullable Dimension dimension;
	private volatile int publisherRadius = Integer.MAX_VALUE;
	private volatile int chunkRadius = Integer.MAX_VALUE;
	private volatile int centerX;
	private volatile int centerZ;

	/**
	 * Creates the tracker of one connection.
	 *
	 * @param protocol protocol number of the connection's codec
	 * @param sender sends a packet on the connection, such as a sub-chunk request
	 */
	public WorldTracker(int protocol, @NotNull Consumer<BedrockPacket> sender) {
		this.protocol = protocol;
		this.sender = sender;
	}

	/**
	 * Returns the runtime id of a block of the dimension the player is in.
	 *
	 * @param x block x coordinate
	 * @param y block y coordinate
	 * @param z block z coordinate
	 * @return the block's first-layer runtime id, or empty when the client does not know it
	 */
	public @NotNull OptionalInt blockAt(int x, int y, int z) {
		Dimension current = dimension;
		if (current == null) return OptionalInt.empty();

		BlockSection section = current.section(x, y, z);
		if (section == null) return OptionalInt.empty();

		return section.get(BlockSection.index(x, y, z));
	}

	@Override
	public PacketSignal handle(DimensionDataPacket packet) {
		for (DimensionDefinition definition : packet.getDefinitions()) {
			Integer id = DIMENSION_IDS.get(definition.getId());
			if (id != null) heights.put(id, new Height(definition.getMinimumHeight(), definition.getMaximumHeight()));
		}
		return PacketSignal.UNHANDLED;
	}

	@Override
	public PacketSignal handle(StartGamePacket packet) {
		enter(packet.getDimensionId(), packet.getPlayerPosition());
		return PacketSignal.UNHANDLED;
	}

	@Override
	public PacketSignal handle(ChangeDimensionPacket packet) {
		enter(packet.getDimension(), packet.getPosition());
		return PacketSignal.UNHANDLED;
	}

	@Override
	public PacketSignal handle(LevelChunkPacket packet) {
		Dimension current = dimension;
		if (current == null) return PacketSignal.UNHANDLED;

		// Older codecs send the chunks of the dimension the client is in without naming it.
		int chunkDimension = protocol >= CHUNK_DIMENSION_PROTOCOL ? packet.getDimension() : current.id;
		if (!current.matches(chunkDimension)) return PacketSignal.UNHANDLED;

		try {
			current.load(packet);
		} catch (RuntimeException failure) {
			current.columns.remove(key(packet.getChunkX(), packet.getChunkZ()));
			report("level chunk " + packet.getChunkX() + " " + packet.getChunkZ(), failure);
		}
		return PacketSignal.UNHANDLED;
	}

	@Override
	public PacketSignal handle(SubChunkPacket packet) {
		Dimension current = dimension;
		if (current == null || !current.matches(packet.getDimension())) return PacketSignal.UNHANDLED;

		Vector3i center = packet.getCenterPosition();
		for (SubChunkData subChunk : packet.getSubChunks()) {
			Vector3i position = center.add(subChunk.getPosition());
			try {
				current.receive(position, subChunk);
			} catch (RuntimeException failure) {
				current.replace(position.getX(), position.getY(), position.getZ(), null);
				report("sub-chunk " + position, failure);
			}
		}
		return PacketSignal.UNHANDLED;
	}

	@Override
	public PacketSignal handle(UpdateBlockPacket packet) {
		Dimension current = dimension;
		if (current == null || packet.getDataLayer() != 0) return PacketSignal.UNHANDLED;

		current.update(packet.getBlockPosition(), packet.getDefinition().getRuntimeId());
		return PacketSignal.UNHANDLED;
	}

	@Override
	public PacketSignal handle(UpdateSubChunkBlocksPacket packet) {
		Dimension current = dimension;
		if (current == null) return PacketSignal.UNHANDLED;

		// The standard blocks are the first layer; the extra blocks are the waterlogging layer.
		for (BlockChangeEntry entry : packet.getStandardBlocks())
			current.update(entry.getPosition(), entry.getDefinition().getRuntimeId());
		return PacketSignal.UNHANDLED;
	}

	@Override
	public PacketSignal handle(NetworkChunkPublisherUpdatePacket packet) {
		centerX = packet.getPosition().getX();
		centerZ = packet.getPosition().getZ();
		publisherRadius = packet.getRadius();
		evict();
		return PacketSignal.UNHANDLED;
	}

	@Override
	public PacketSignal handle(ChunkRadiusUpdatedPacket packet) {
		chunkRadius = packet.getRadius();
		evict();
		return PacketSignal.UNHANDLED;
	}

	private void enter(int id, @Nullable Vector3f position) {
		if (position != null) {
			centerX = position.getFloorX();
			centerZ = position.getFloorZ();
		}
		publisherRadius = Integer.MAX_VALUE;

		Height height = heights.get(id);
		dimension = height == null ? null : new Dimension(id, height);
		if (height == null)
			System.err.println("[Anvil] The client does not know the height of dimension " + id + " and tracks none of its blocks");
	}

	private void evict() {
		Dimension current = dimension;
		if (current == null) return;

		// Until the server names a radius, every chunk it sent stays.
		long radius = Math.min(publisherRadius, chunkRadius * 16L);
		if (radius >= Integer.MAX_VALUE) return;

		long reach = radius + RADIUS_MARGIN;
		int x = centerX;
		int z = centerZ;
		current.columns.keySet().removeIf(key -> {
			long dx = (chunkX(key) << 4) + 8L - x;
			long dz = (chunkZ(key) << 4) + 8L - z;
			return dx * dx + dz * dz > reach * reach;
		});
	}

	private static long key(int chunkX, int chunkZ) {
		return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
	}

	private static int chunkX(long key) {
		return (int) (key >> 32);
	}

	private static int chunkZ(long key) {
		return (int) key;
	}

	private static void report(String subject, RuntimeException failure) {
		System.err.println("[Anvil] The client could not read the " + subject + " and forgot its blocks: " + failure);
	}

	/**
	 * The lowest block and the block above the highest one of a dimension.
	 */
	private record Height(int minimum, int maximum) {
		private int lowestSection() {
			return minimum >> 4;
		}

		private int sections() {
			return (maximum - minimum + 15) >> 4;
		}
	}

	/**
	 * The chunks the client holds of the dimension it is in.
	 */
	private final class Dimension {
		private final int id;
		private final Height height;
		private final Map<Long, AtomicReferenceArray<BlockSection>> columns = new ConcurrentHashMap<>();

		private Dimension(int id, Height height) {
			this.id = id;
			this.height = height;
		}

		private boolean matches(int dimension) {
			return dimension == id;
		}

		private @Nullable BlockSection section(int x, int y, int z) {
			AtomicReferenceArray<BlockSection> column = columns.get(key(x >> 4, z >> 4));
			int index = (y >> 4) - height.lowestSection();
			if (column == null || index < 0 || index >= column.length()) return null;

			return column.get(index);
		}

		/**
		 * Replaces a chunk by the one a level chunk carries, or requests its sub-chunks when the server sends them
		 * on request.
		 */
		private void load(LevelChunkPacket packet) {
			long key = key(packet.getChunkX(), packet.getChunkZ());
			AtomicReferenceArray<BlockSection> column = new AtomicReferenceArray<>(height.sections());
			if (packet.isRequestSubChunks()) {
				columns.put(key, column);
				request(packet.getChunkX(), packet.getChunkZ(), packet.getSubChunkLimit());
				return;
			}
			if (packet.isCachingEnabled())
				throw new IllegalStateException("the server sent the chunk as cached blobs, which the client does not support");

			// The first sub-chunk of the data is the dimension's lowest.
			ByteBuf data = packet.getData().duplicate();
			int count = Math.min(packet.getSubChunksLength(), column.length());
			for (int index = 0; index < count; index++)
				column.set(index, SubChunkReader.read(data));
			columns.put(key, column);
		}

		/**
		 * Requests the sub-chunks of a chunk from the dimension's lowest on: all of them, or as many as the limit the
		 * server named.
		 */
		private void request(int chunkX, int chunkZ, int limit) {
			int count = limit < 0 ? height.sections() : Math.min(limit, height.sections());
			if (count == 0) return;

			SubChunkRequestPacket request = new SubChunkRequestPacket();
			request.setDimension(id);
			request.setSubChunkPosition(Vector3i.from(chunkX, 0, chunkZ));
			List<Vector3i> offsets = request.getPositionOffsets();
			for (int index = 0; index < count; index++)
				offsets.add(Vector3i.from(0, height.lowestSection() + index, 0));
			sender.accept(request);
		}

		/**
		 * Stores a sub-chunk the server answered a request with.
		 */
		private void receive(Vector3i position, SubChunkData subChunk) {
			switch (subChunk.getResult()) {
				case SUCCESS -> {
					ByteBuf data = subChunk.getData();
					if (data == null)
						throw new IllegalStateException("the server sent the sub-chunk as a cached blob, which the client does not support");

					replace(position.getX(), position.getY(), position.getZ(), SubChunkReader.read(data.duplicate()));
				}
				case SUCCESS_ALL_AIR -> replace(position.getX(), position.getY(), position.getZ(), null);
				default -> {
					// The server has no such sub-chunk for the client; it stays as it was.
				}
			}
		}

		private void replace(int chunkX, int sectionY, int chunkZ, @Nullable BlockSection section) {
			AtomicReferenceArray<BlockSection> column = columns.get(key(chunkX, chunkZ));
			int index = sectionY - height.lowestSection();
			if (column == null || index < 0 || index >= column.length()) return;

			column.set(index, section);
		}

		/**
		 * Applies a first-layer block update to a chunk the client holds; a client ignores updates of other chunks.
		 */
		private void update(Vector3i position, int runtimeId) {
			AtomicReferenceArray<BlockSection> column = columns.get(key(position.getX() >> 4, position.getZ() >> 4));
			int index = (position.getY() >> 4) - height.lowestSection();
			if (column == null || index < 0 || index >= column.length()) return;

			BlockSection section = column.get(index);
			if (section == null) {
				column.compareAndSet(index, null, BlockSection.unknown());
				section = column.get(index);
			}
			section.set(BlockSection.index(position.getX(), position.getY(), position.getZ()), runtimeId);
		}
	}
}
