package me.whereareiam.anvil.protocol.cloudburst.client.world.v1_26_60;

import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.cloudburstmc.protocol.common.util.VarInts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads sub-chunks in the network format servers send to Bedrock clients: a version byte, then for version 8 the
 * number of block storages, for version 9 that number and the sub-chunk's y index, and for version 1 a single
 * storage. Each storage starts with a header whose low bit marks runtime ids and whose other bits give the bits per
 * block; it continues with the packed palette indices in little-endian 32-bit words, each word holding as many
 * whole indices as fit, the palette size and the palette's runtime ids as zigzag varints. A storage of zero bits per
 * block has no words and no size, only the single runtime id of all its blocks.
 *
 * <p>Only the first storage, the block layer, is kept; the others are read past. The y index of version 9 is read
 * and not trusted: the position a sub-chunk was sent for places it, as for a client.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SubChunkReader {
	private static final int MAX_PALETTE = 1 << 16;

	/**
	 * Reads one sub-chunk and advances the buffer past it.
	 *
	 * @param buffer network data positioned at the sub-chunk
	 * @return the sub-chunk's block layer, or {@code null} when the sub-chunk has no block storage
	 * @throws IllegalArgumentException when the data is no network sub-chunk of a supported version
	 * @throws IndexOutOfBoundsException when the data ends inside the sub-chunk
	 */
	static @Nullable BlockSection read(@NotNull ByteBuf buffer) {
		int version = buffer.readUnsignedByte();
		int storages = switch (version) {
			case 1 -> 1;
			case 8 -> buffer.readUnsignedByte();
			case 9 -> {
				int count = buffer.readUnsignedByte();
				buffer.readByte();
				yield count;
			}
			default -> throw new IllegalArgumentException("Unsupported network sub-chunk version " + version);
		};

		BlockSection blocks = null;
		for (int storage = 0; storage < storages; storage++) {
			BlockSection layer = storage(buffer);
			if (storage == 0) blocks = layer;
		}

		return blocks;
	}

	private static BlockSection storage(ByteBuf buffer) {
		int header = buffer.readUnsignedByte();
		if ((header & 1) == 0)
			throw new IllegalArgumentException("The sub-chunk storage names its palette by block states instead of runtime ids");

		int bits = header >>> 1;
		if (bits == 0) return BlockSection.uniform(VarInts.readInt(buffer));
		if (!supported(bits)) throw new IllegalArgumentException("Unsupported sub-chunk storage of " + bits + " bits per block");

		int perWord = Integer.SIZE / bits;
		int[] words = new int[(BlockSection.BLOCKS + perWord - 1) / perWord];
		for (int i = 0; i < words.length; i++)
			words[i] = buffer.readIntLE();

		int size = VarInts.readInt(buffer);
		if (size < 1 || size > MAX_PALETTE) throw new IllegalArgumentException("Invalid sub-chunk palette size " + size);

		int[] palette = new int[size];
		for (int i = 0; i < size; i++)
			palette[i] = VarInts.readInt(buffer);

		return BlockSection.paletted(palette, indices(words, bits, perWord, size));
	}

	private static short[] indices(int[] words, int bits, int perWord, int size) {
		int mask = (1 << bits) - 1;
		short[] indices = new short[BlockSection.BLOCKS];
		for (int block = 0; block < BlockSection.BLOCKS; block++) {
			int index = (words[block / perWord] >>> (block % perWord * bits)) & mask;
			if (index >= size)
				throw new IllegalArgumentException("Block " + block + " names palette entry " + index + " of " + size);

			indices[block] = (short) index;
		}

		return indices;
	}

	private static boolean supported(int bits) {
		return switch (bits) {
			case 1, 2, 3, 4, 5, 6, 8, 16 -> true;
			default -> false;
		};
	}
}
