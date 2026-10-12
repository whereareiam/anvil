package me.whereareiam.anvil.protocol.cloudburst.client.world.v1_26_60;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v686.Bedrock_v686;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;
import org.cloudburstmc.protocol.bedrock.codec.v859.Bedrock_v859;
import org.cloudburstmc.protocol.bedrock.codec.v860.Bedrock_v860;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.codec.v924.Bedrock_v924;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.cloudburstmc.protocol.bedrock.codec.v975.Bedrock_v975;
import org.cloudburstmc.protocol.bedrock.codec.v1001.Bedrock_v1001;
import org.cloudburstmc.protocol.bedrock.codec.v2168.Bedrock_v2168;
import org.cloudburstmc.protocol.bedrock.codec.v2169.Bedrock_v2169;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry;
import org.cloudburstmc.protocol.bedrock.data.HeightMapDataType;
import org.cloudburstmc.protocol.bedrock.data.SubChunkData;
import org.cloudburstmc.protocol.bedrock.data.SubChunkRequestResult;
import org.cloudburstmc.protocol.bedrock.data.definitions.DimensionDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
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
import org.cloudburstmc.protocol.common.util.VarInts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Feeds the tracker packets that the release's codecs decoded from the bytes a server sends.
 */
class WorldTrackerTest {
	private static final List<BedrockCodec> CODECS = List.of(
			Bedrock_v589.CODEC,
			Bedrock_v594.CODEC,
			Bedrock_v618.CODEC,
			Bedrock_v622.CODEC,
			Bedrock_v630.CODEC,
			Bedrock_v649.CODEC,
			Bedrock_v662.CODEC,
			Bedrock_v671.CODEC,
			Bedrock_v685.CODEC,
			Bedrock_v686.CODEC,
			Bedrock_v712.CODEC,
			Bedrock_v729.CODEC,
			Bedrock_v748.CODEC,
			Bedrock_v766.CODEC,
			Bedrock_v776.CODEC,
			Bedrock_v786.CODEC,
			Bedrock_v800.CODEC,
			Bedrock_v818.CODEC,
			Bedrock_v819.CODEC,
			Bedrock_v827.CODEC,
			Bedrock_v844.CODEC,
			Bedrock_v859.CODEC,
			Bedrock_v860.CODEC,
			Bedrock_v898.CODEC,
			Bedrock_v924.CODEC,
			Bedrock_v944.CODEC,
			Bedrock_v975.CODEC,
			Bedrock_v1001.CODEC,
			Bedrock_v2168.CODEC,
			Bedrock_v2169.CODEC,
			Bedrock_v2193.CODEC,
			Bedrock_v2225.CODEC
	);
	private static final int OVERWORLD = 0;
	private static final int NETHER = 1;
	private static final int STONE = 1;
	private static final int DIRT = 2;
	private static final int GRASS = 3;
	private static final int BEDROCK = 4;
	private static final int UNLIMITED = -1;
	/**
	 * Bytes after the sub-chunks, standing for biomes, border blocks and block entities, which the tracker never reads.
	 */
	private static final byte[] TRAILER = {(byte) 0xFF, 0x7F, 0x01, 0x00};

	private final List<BedrockPacket> sent = new ArrayList<>();

	static List<BedrockCodec> codecs() {
		return CODECS;
	}

	@ParameterizedTest
	@MethodSource("codecs")
	void readsTheSubChunksOfAFullLevelChunkFromTheDimensionsLowestOn(BedrockCodec codec) {
		WorldTracker tracker = started(codec, OVERWORLD);
		ByteBuf data = Unpooled.buffer();
		// Sub-chunks -4 to -1 hold only air, 0 holds a terrain layer and 1 is all grass.
		for (int air = 0; air < 4; air++)
			SubChunkWriter.version8().write(data);
		int[] indices = new int[4096];
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				indices[SubChunkWriter.index(x, 0, z)] = 1;
				indices[SubChunkWriter.index(x, 1, z)] = 2;
			}
		SubChunkWriter.version8().paletted(2, new int[]{STONE, BEDROCK, DIRT}, indices).write(data);
		SubChunkWriter.version9(1).uniform(GRASS).write(data);
		data.writeBytes(TRAILER);

		tracker.handle(decode(codec, fullChunk(codec, 2, -3, OVERWORLD, 6, data)));

		assertEquals(OptionalInt.of(BEDROCK), tracker.blockAt(32, 0, -48));
		assertEquals(OptionalInt.of(DIRT), tracker.blockAt(47, 1, -33));
		assertEquals(OptionalInt.of(STONE), tracker.blockAt(40, 15, -40));
		assertEquals(OptionalInt.of(GRASS), tracker.blockAt(40, 31, -40));
		// Air sub-chunks, sub-chunks above the sent ones, other chunks and blocks beyond the height are unknown.
		assertEquals(OptionalInt.empty(), tracker.blockAt(40, -64, -40));
		assertEquals(OptionalInt.empty(), tracker.blockAt(40, 32, -40));
		assertEquals(OptionalInt.empty(), tracker.blockAt(48, 0, -40));
		assertEquals(OptionalInt.empty(), tracker.blockAt(40, 320, -40));
		assertEquals(OptionalInt.empty(), tracker.blockAt(40, -65, -40));
	}

	@Test
	void readsTheSpawnChunkALumiServerSentToTheNewestAndOldestCodecs() throws IOException {
		// Captured from Lumi 1.6.9 with a generated world: chunk 8 8 holds an ocean over dirt, stone and deepslate
		// on bedrock. Lumi hashes block network ids, so the ids are block state hashes, which differ between the
		// protocols where a block's states differ.
		BedrockCodec newest = Bedrock_v2193.CODEC;
		WorldTracker current = started(newest, OVERWORLD);
		current.handle(decode(newest, fullChunk(newest, 8, 8, OVERWORLD, 24, captured("lumi-chunk-v2193.bin.gz"))));
		BedrockCodec oldest = Bedrock_v589.CODEC;
		WorldTracker first = started(oldest, OVERWORLD);
		first.handle(decode(oldest, fullChunk(oldest, 8, 8, OVERWORLD, 24, captured("lumi-chunk-v589.bin.gz"))));

		int bedrock = -173245189;
		int deepslate = 994207970;
		int water = 1211861802;
		int air = -604749536;
		for (WorldTracker tracker : List.of(current, first)) {
			assertEquals(OptionalInt.of(bedrock), tracker.blockAt(128, -64, 128));
			assertEquals(OptionalInt.of(deepslate), tracker.blockAt(128, -1, 128));
			assertEquals(OptionalInt.of(water), tracker.blockAt(128, 62, 128));
			assertEquals(OptionalInt.of(water), tracker.blockAt(128, 64, 128));
			assertEquals(OptionalInt.of(air), tracker.blockAt(128, 65, 128));
		}
		assertEquals(OptionalInt.of(-2108756090), current.blockAt(128, 61, 128));
		assertEquals(OptionalInt.of(-2144268767), current.blockAt(128, 0, 128));
		// At protocol 589 dirt still has its dirt type state.
		assertEquals(OptionalInt.of(1745588518), first.blockAt(128, 61, 128));
	}

	@ParameterizedTest
	@MethodSource("codecs")
	void requestsTheSubChunksOfAChunkSentOnRequestAndReadsTheResponses(BedrockCodec codec) throws Exception {
		WorldTracker tracker = started(codec, OVERWORLD);

		tracker.handle(decode(codec, requestedChunk(codec, -1, 5, OVERWORLD, 6)));

		SubChunkRequestPacket request = assertInstanceOf(SubChunkRequestPacket.class, sent.getLast());
		assertEquals(OVERWORLD, request.getDimension());
		assertEquals(Vector3i.from(-1, 0, 5), request.getSubChunkPosition());
		List<Vector3i> offsets = new ArrayList<>();
		for (int y = -4; y < 2; y++)
			offsets.add(Vector3i.from(0, y, 0));
		assertEquals(offsets, request.getPositionOffsets());
		// The request is one the codec can send.
		codec.tryEncode(codec.createHelper(), Unpooled.buffer(), request);

		SubChunkPacket response = new SubChunkPacket();
		response.setDimension(OVERWORLD);
		response.setCenterPosition(Vector3i.from(-1, 0, 5));
		response.getSubChunks().add(subChunk(Vector3i.from(0, 0, 0), SubChunkRequestResult.SUCCESS, SubChunkWriter.version9(0).uniform(STONE).buffer()));
		response.getSubChunks().add(subChunk(Vector3i.from(0, 1, 0), SubChunkRequestResult.SUCCESS_ALL_AIR, Unpooled.EMPTY_BUFFER));
		response.getSubChunks().add(subChunk(Vector3i.from(0, -4, 0), SubChunkRequestResult.SUCCESS, SubChunkWriter.version8().uniform(BEDROCK).buffer()));
		response.getSubChunks().add(subChunk(Vector3i.from(1, 0, 0), SubChunkRequestResult.SUCCESS, SubChunkWriter.version8().uniform(DIRT).buffer()));
		tracker.handle(roundTrip(codec, response));

		assertEquals(OptionalInt.of(STONE), tracker.blockAt(-16, 0, 80));
		assertEquals(OptionalInt.of(BEDROCK), tracker.blockAt(-1, -64, 95));
		assertEquals(OptionalInt.empty(), tracker.blockAt(-16, 16, 80));
		// A sub-chunk of a chunk the client holds no level chunk for stays unknown.
		assertEquals(OptionalInt.empty(), tracker.blockAt(0, 0, 80));
	}

	@Test
	void requestsEverySubChunkOfTheDimensionWhenTheServerNamesNoLimit() {
		BedrockCodec older = Bedrock_v630.CODEC;
		WorldTracker overworld = started(older, OVERWORLD);
		overworld.handle(decode(older, requestedChunk(older, 0, 0, OVERWORLD, UNLIMITED)));
		assertEquals(24, assertInstanceOf(SubChunkRequestPacket.class, sent.getLast()).getPositionOffsets().size());

		BedrockCodec newer = Bedrock_v2193.CODEC;
		WorldTracker nether = started(newer, NETHER);
		nether.handle(decode(newer, requestedChunk(newer, 0, 0, NETHER, UNLIMITED)));

		SubChunkRequestPacket request = assertInstanceOf(SubChunkRequestPacket.class, sent.getLast());
		assertEquals(NETHER, request.getDimension());
		assertEquals(8, request.getPositionOffsets().size());
		assertEquals(Vector3i.from(0, 7, 0), request.getPositionOffsets().getLast());
	}

	@Test
	void appliesBlockUpdatesOfTheFirstLayerToChunksItHolds() {
		BedrockCodec codec = Bedrock_v2225.CODEC;
		WorldTracker tracker = started(codec, OVERWORLD);
		tracker.handle(decode(codec, fullChunk(codec, 0, 0, OVERWORLD, 1, SubChunkWriter.version8().uniform(STONE).buffer())));

		tracker.handle(update(Vector3i.from(1, -64, 1), DIRT, 0));
		tracker.handle(update(Vector3i.from(2, -64, 2), GRASS, 1));
		// A block of a sub-chunk sent without blocks becomes known alone; one of a chunk not held is ignored.
		tracker.handle(update(Vector3i.from(3, 100, 3), GRASS, 0));
		tracker.handle(update(Vector3i.from(16, 0, 0), GRASS, 0));
		UpdateSubChunkBlocksPacket blocks = new UpdateSubChunkBlocksPacket();
		blocks.setPosition(Vector3i.from(0, -4, 0));
		blocks.getStandardBlocks().add(new BlockChangeEntry(Vector3i.from(4, -60, 4), () -> BEDROCK, 3, -1, BlockChangeEntry.MessageType.NONE));
		blocks.getExtraBlocks().add(new BlockChangeEntry(Vector3i.from(5, -60, 5), () -> GRASS, 3, -1, BlockChangeEntry.MessageType.NONE));
		tracker.handle(blocks);

		assertEquals(OptionalInt.of(DIRT), tracker.blockAt(1, -64, 1));
		assertEquals(OptionalInt.of(STONE), tracker.blockAt(2, -64, 2));
		assertEquals(OptionalInt.of(GRASS), tracker.blockAt(3, 100, 3));
		assertEquals(OptionalInt.empty(), tracker.blockAt(3, 101, 3));
		assertEquals(OptionalInt.empty(), tracker.blockAt(16, 0, 0));
		assertEquals(OptionalInt.of(BEDROCK), tracker.blockAt(4, -60, 4));
		assertEquals(OptionalInt.of(STONE), tracker.blockAt(5, -60, 5));
	}

	@Test
	void keepsEveryBlockThroughManyDistinctUpdatesOfOneSubChunk() {
		BedrockCodec codec = Bedrock_v2225.CODEC;
		WorldTracker tracker = started(codec, OVERWORLD);
		tracker.handle(decode(codec, fullChunk(codec, 0, 0, OVERWORLD, 1, SubChunkWriter.version8().uniform(STONE).buffer())));

		for (int round = 0; round < 3; round++)
			for (int block = 0; block < 4096; block++)
				tracker.handle(update(position(block), round * 4096 + block, 0));

		for (int block = 0; block < 4096; block++) {
			Vector3i position = position(block);
			assertEquals(OptionalInt.of(2 * 4096 + block), tracker.blockAt(position.getX(), position.getY(), position.getZ()));
		}
	}

	@Test
	void forgetsTheChunksOfTheDimensionItLeavesAndThoseThePublisherNoLongerCovers() {
		BedrockCodec codec = Bedrock_v2225.CODEC;
		WorldTracker tracker = started(codec, OVERWORLD);
		for (int chunkX = 0; chunkX < 20; chunkX++)
			tracker.handle(decode(codec, fullChunk(codec, chunkX, 0, OVERWORLD, 1, SubChunkWriter.version8().uniform(STONE).buffer())));

		NetworkChunkPublisherUpdatePacket publisher = new NetworkChunkPublisherUpdatePacket();
		publisher.setPosition(Vector3i.from(8, 70, 8));
		publisher.setRadius(4 << 4);
		tracker.handle(publisher);

		assertEquals(OptionalInt.of(STONE), tracker.blockAt(4 * 16, -64, 0));
		// The margin keeps the chunks a server loads around the player's chunk.
		assertEquals(OptionalInt.of(STONE), tracker.blockAt(6 * 16, -64, 0));
		assertEquals(OptionalInt.empty(), tracker.blockAt(8 * 16, -64, 0));

		ChunkRadiusUpdatedPacket radius = new ChunkRadiusUpdatedPacket();
		radius.setRadius(1);
		tracker.handle(radius);
		assertEquals(OptionalInt.of(STONE), tracker.blockAt(16, -64, 0));
		assertEquals(OptionalInt.empty(), tracker.blockAt(5 * 16, -64, 0));

		ChangeDimensionPacket change = new ChangeDimensionPacket();
		change.setDimension(NETHER);
		change.setPosition(Vector3f.from(0, 70, 0));
		tracker.handle(change);
		assertEquals(OptionalInt.empty(), tracker.blockAt(0, -64, 0));
		tracker.handle(decode(codec, fullChunk(codec, 0, 0, NETHER, 1, SubChunkWriter.version8().uniform(DIRT).buffer())));
		assertEquals(OptionalInt.of(DIRT), tracker.blockAt(0, 0, 0));
	}

	@Test
	void ignoresChunksOfAnotherDimensionAndPlacesThoseOfOlderCodecsInTheCurrentOne() {
		BedrockCodec newer = Bedrock_v649.CODEC;
		WorldTracker tracker = started(newer, NETHER);
		tracker.handle(decode(newer, fullChunk(newer, 0, 0, OVERWORLD, 1, SubChunkWriter.version8().uniform(STONE).buffer())));
		assertEquals(OptionalInt.empty(), tracker.blockAt(0, 0, 0));

		BedrockCodec older = Bedrock_v630.CODEC;
		WorldTracker nether = started(older, NETHER);
		nether.handle(decode(older, fullChunk(older, 0, 0, OVERWORLD, 1, SubChunkWriter.version8().uniform(STONE).buffer())));
		assertEquals(OptionalInt.of(STONE), nether.blockAt(0, 0, 0));
	}

	@Test
	void takesTheHeightOfADimensionFromTheDimensionDataTheServerSent() {
		BedrockCodec codec = Bedrock_v2225.CODEC;
		WorldTracker tracker = new WorldTracker(codec.getProtocolVersion(), sent::add);
		DimensionDataPacket dimensions = new DimensionDataPacket();
		dimensions.getDefinitions().add(new DimensionDefinition("minecraft:overworld", 256, 0, 5, 0, null, null, 0, false));
		tracker.handle(dimensions);
		tracker.handle(start(OVERWORLD));

		tracker.handle(decode(codec, fullChunk(codec, 0, 0, OVERWORLD, 1, SubChunkWriter.version8().uniform(STONE).buffer())));

		assertEquals(OptionalInt.of(STONE), tracker.blockAt(0, 0, 0));
		assertEquals(OptionalInt.empty(), tracker.blockAt(0, -1, 0));
	}

	@Test
	void forgetsAChunkItCannotRead() {
		BedrockCodec codec = Bedrock_v2225.CODEC;
		WorldTracker tracker = started(codec, OVERWORLD);
		tracker.handle(decode(codec, fullChunk(codec, 0, 0, OVERWORLD, 1, SubChunkWriter.version8().uniform(STONE).buffer())));

		tracker.handle(decode(codec, fullChunk(codec, 0, 0, OVERWORLD, 1, Unpooled.wrappedBuffer(new byte[]{8, 1, 4 << 1 | 1}))));

		assertEquals(OptionalInt.empty(), tracker.blockAt(0, -64, 0));
	}

	private static ByteBuf captured(String name) throws IOException {
		try (InputStream resource = WorldTrackerTest.class.getResourceAsStream(name)) {
			assertNotNull(resource, name);
			try (GZIPInputStream data = new GZIPInputStream(resource)) {
				return Unpooled.wrappedBuffer(data.readAllBytes());
			}
		}
	}

	private WorldTracker started(BedrockCodec codec, int dimension) {
		WorldTracker tracker = new WorldTracker(codec.getProtocolVersion(), sent::add);
		tracker.handle(start(dimension));
		return tracker;
	}

	private static StartGamePacket start(int dimension) {
		StartGamePacket start = new StartGamePacket();
		start.setDimensionId(dimension);
		start.setPlayerPosition(Vector3f.from(8, 70, 8));
		return start;
	}

	private static Vector3i position(int block) {
		return Vector3i.from(block >> 8, -64 + (block & 15), block >> 4 & 15);
	}

	private static UpdateBlockPacket update(Vector3i position, int runtimeId, int layer) {
		UpdateBlockPacket packet = new UpdateBlockPacket();
		packet.setBlockPosition(position);
		packet.setDefinition(() -> runtimeId);
		packet.setDataLayer(layer);
		return packet;
	}

	private static SubChunkData subChunk(Vector3i offset, SubChunkRequestResult result, ByteBuf data) {
		SubChunkData subChunk = new SubChunkData();
		subChunk.setPosition(offset);
		subChunk.setResult(result);
		subChunk.setData(data);
		subChunk.setHeightMapType(HeightMapDataType.NO_DATA);
		subChunk.setRenderHeightMapType(HeightMapDataType.NO_DATA);
		return subChunk;
	}

	/**
	 * Writes a level chunk that carries its sub-chunks, as a server encodes it for the codec's protocol.
	 */
	private static ByteBuf fullChunk(BedrockCodec codec, int chunkX, int chunkZ, int dimension, int subChunks, ByteBuf data) {
		int protocol = codec.getProtocolVersion();
		ByteBuf out = location(protocol, chunkX, chunkZ, dimension);
		VarInts.writeUnsignedInt(out, subChunks);
		if (protocol >= 2168) out.writeBoolean(false);
		caching(protocol, out);
		VarInts.writeUnsignedInt(out, data.readableBytes());
		out.writeBytes(data);
		if (protocol >= 2225) out.writeBoolean(false);
		return out;
	}

	/**
	 * Writes a level chunk whose sub-chunks the client requests, up to a limit or {@link #UNLIMITED}.
	 */
	private static ByteBuf requestedChunk(BedrockCodec codec, int chunkX, int chunkZ, int dimension, int limit) {
		int protocol = codec.getProtocolVersion();
		ByteBuf out = location(protocol, chunkX, chunkZ, dimension);
		if (protocol >= 2168) {
			VarInts.writeUnsignedInt(out, 0);
			out.writeBoolean(true);
			VarInts.writeInt(out, limit);
		} else if (limit == UNLIMITED) {
			VarInts.writeUnsignedInt(out, -1);
		} else {
			VarInts.writeUnsignedInt(out, -2);
			out.writeShortLE(limit);
		}
		caching(protocol, out);
		VarInts.writeUnsignedInt(out, 0);
		if (protocol >= 2225) out.writeBoolean(false);
		return out;
	}

	private static ByteBuf location(int protocol, int chunkX, int chunkZ, int dimension) {
		ByteBuf out = Unpooled.buffer();
		VarInts.writeInt(out, chunkX);
		VarInts.writeInt(out, chunkZ);
		if (protocol >= 649) VarInts.writeInt(out, dimension);
		return out;
	}

	private static void caching(int protocol, ByteBuf out) {
		out.writeBoolean(false);
		if (protocol >= 2168) VarInts.writeUnsignedInt(out, 0);
	}

	private static LevelChunkPacket decode(BedrockCodec codec, ByteBuf bytes) {
		try {
			LevelChunkPacket packet = (LevelChunkPacket) codec.tryDecode(codec.createHelper(), bytes, codec.getPacketDefinition(LevelChunkPacket.class).getId());
			assertFalse(bytes.isReadable(), "the codec reads the whole level chunk");
			return packet;
		} catch (Exception exception) {
			throw new AssertionError("The codec could not read the level chunk", exception);
		}
	}

	private static SubChunkPacket roundTrip(BedrockCodec codec, SubChunkPacket packet) throws Exception {
		BedrockCodecHelper helper = codec.createHelper();
		ByteBuf bytes = Unpooled.buffer();
		codec.tryEncode(helper, bytes, packet);
		return (SubChunkPacket) codec.tryDecode(helper, bytes, codec.getPacketDefinition(SubChunkPacket.class).getId());
	}
}
