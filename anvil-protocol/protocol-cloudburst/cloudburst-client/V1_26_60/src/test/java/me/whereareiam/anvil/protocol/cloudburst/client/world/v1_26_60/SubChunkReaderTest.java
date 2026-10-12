package me.whereareiam.anvil.protocol.cloudburst.client.world.v1_26_60;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubChunkReaderTest {
	/**
	 * A block state hash, as servers that hash block network ids send them: negative as an int.
	 */
	private static final int HASHED = 0x9A3B1C2D;

	@ParameterizedTest
	@ValueSource(ints = {1, 2, 3, 4, 5, 6, 8, 16})
	void readsEveryBitsPerBlockIncludingTheWordsWhosePaddingLeavesBitsUnused(int bits) {
		int size = Math.min(1 << bits, 300);
		int[] palette = new int[size];
		for (int i = 0; i < size; i++)
			palette[i] = i == 1 ? HASHED : 1000 + i;
		int[] indices = new int[4096];
		for (int block = 0; block < 4096; block++)
			indices[block] = block * 7 % size;
		ByteBuf data = SubChunkWriter.version8().paletted(bits, palette, indices).buffer();
		data.writeByte(0x55);

		BlockSection section = SubChunkReader.read(data);

		for (int block = 0; block < 4096; block++)
			assertEquals(OptionalInt.of(palette[indices[block]]), section.get(block), "block " + block);
		assertEquals(0x55, data.readUnsignedByte());
	}

	@Test
	void readsAStorageOfZeroBitsAsTheSingleRuntimeIdOfEveryBlock() {
		BlockSection section = SubChunkReader.read(SubChunkWriter.version8().uniform(HASHED).buffer());

		assertEquals(OptionalInt.of(HASHED), section.get(0));
		assertEquals(OptionalInt.of(HASHED), section.get(4095));
	}

	@Test
	void keepsTheBlockLayerAndReadsPastTheWaterloggingLayer() {
		int[] indices = new int[4096];
		indices[SubChunkWriter.index(3, 4, 5)] = 1;
		ByteBuf data = SubChunkWriter.version8()
				.paletted(1, new int[]{10, 11}, indices)
				.paletted(2, new int[]{20, 21, 22}, new int[4096])
				.buffer();
		data.writeByte(0x55);

		BlockSection section = SubChunkReader.read(data);

		assertEquals(OptionalInt.of(11), section.get(SubChunkWriter.index(3, 4, 5)));
		assertEquals(OptionalInt.of(10), section.get(SubChunkWriter.index(3, 5, 5)));
		assertEquals(0x55, data.readUnsignedByte());
	}

	@Test
	void readsVersionNineWithItsYIndexAndVersionOneWithItsSingleStorage() {
		assertEquals(OptionalInt.of(7), SubChunkReader.read(SubChunkWriter.version9(-4).uniform(7).buffer()).get(0));
		assertEquals(OptionalInt.of(8), SubChunkReader.read(SubChunkWriter.version1().uniform(8).buffer()).get(0));
	}

	@Test
	void hasNoBlocksForASubChunkWithoutStorage() {
		ByteBuf data = SubChunkWriter.version8().buffer();
		data.writeByte(0x55);

		assertNull(SubChunkReader.read(data));
		assertEquals(0x55, data.readUnsignedByte());
	}

	@Test
	void refusesWhatNoBedrockServerSendsAClient() {
		assertThrows(IllegalArgumentException.class, () -> SubChunkReader.read(Unpooled.wrappedBuffer(new byte[]{0, 0})));
		assertThrows(IllegalArgumentException.class, () -> SubChunkReader.read(Unpooled.wrappedBuffer(new byte[]{8, 1, 2})));
		assertThrows(IllegalArgumentException.class, () -> SubChunkReader.read(Unpooled.wrappedBuffer(new byte[]{8, 1, 7 << 1 | 1})));
		int[] indices = new int[4096];
		indices[100] = 3;
		ByteBuf outOfPalette = SubChunkWriter.version8().paletted(2, new int[]{1, 2}, indices).buffer();
		assertThrows(IllegalArgumentException.class, () -> SubChunkReader.read(outOfPalette));
		assertThrows(IndexOutOfBoundsException.class, () -> SubChunkReader.read(Unpooled.wrappedBuffer(new byte[]{8, 1, 4 << 1 | 1, 0})));
	}
}
