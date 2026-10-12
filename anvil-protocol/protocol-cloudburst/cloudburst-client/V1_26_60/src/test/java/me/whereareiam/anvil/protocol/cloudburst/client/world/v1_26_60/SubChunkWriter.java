package me.whereareiam.anvil.protocol.cloudburst.client.world.v1_26_60;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Writes network sub-chunks the way a server encodes them, independently of the reader under test, in the layout of
 * Lumi's {@code LevelDBChunkSection.writeTo} and {@code PalettedBlockStorage.writeTo}.
 */
final class SubChunkWriter {
	private final int version;
	private final int yIndex;
	private final List<Consumer<ByteBuf>> layers = new ArrayList<>();

	private SubChunkWriter(int version, int yIndex) {
		this.version = version;
		this.yIndex = yIndex;
	}

	/**
	 * Starts a sub-chunk of version 8, which lists its storages.
	 */
	static SubChunkWriter version8() {
		return new SubChunkWriter(8, 0);
	}

	/**
	 * Starts a sub-chunk of version 9, which lists its storages and names its y index.
	 */
	static SubChunkWriter version9(int yIndex) {
		return new SubChunkWriter(9, yIndex);
	}

	/**
	 * Starts a sub-chunk of version 1, which holds exactly one storage.
	 */
	static SubChunkWriter version1() {
		return new SubChunkWriter(1, 0);
	}

	/**
	 * Adds a storage whose blocks all have one runtime id.
	 */
	SubChunkWriter uniform(int runtimeId) {
		layers.add(out -> {
			out.writeByte(1);
			VarInts.writeInt(out, runtimeId);
		});
		return this;
	}

	/**
	 * Adds a paletted storage.
	 *
	 * @param bits bits per block
	 * @param palette runtime ids of the palette
	 * @param indices palette index of each of the 4096 blocks in network order
	 */
	SubChunkWriter paletted(int bits, int[] palette, int[] indices) {
		layers.add(out -> {
			out.writeByte(bits << 1 | 1);
			int perWord = 32 / bits;
			int words = (4096 + perWord - 1) / perWord;
			for (int word = 0; word < words; word++) {
				int value = 0;
				for (int slot = 0; slot < perWord; slot++) {
					int block = word * perWord + slot;
					if (block < 4096) value |= indices[block] << (slot * bits);
				}
				out.writeIntLE(value);
			}
			VarInts.writeInt(out, palette.length);
			for (int id : palette)
				VarInts.writeInt(out, id);
		});
		return this;
	}

	/**
	 * Writes the sub-chunk.
	 */
	void write(ByteBuf out) {
		out.writeByte(version);
		if (version != 1) out.writeByte(layers.size());
		if (version == 9) out.writeByte(yIndex);
		layers.forEach(layer -> layer.accept(out));
	}

	/**
	 * Returns the sub-chunk in a buffer of its own.
	 */
	ByteBuf buffer() {
		ByteBuf out = Unpooled.buffer();
		write(out);
		return out;
	}

	/**
	 * Returns the network index of a block inside a sub-chunk.
	 */
	static int index(int x, int y, int z) {
		return (x << 8) | (z << 4) | y;
	}
}
