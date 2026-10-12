package me.whereareiam.anvil.protocol.cloudburst.client.world.v1_26_60;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.OptionalInt;

/**
 * The first block layer of one 16x16x16 sub-chunk as the client knows it: a palette of runtime ids and the palette
 * index of each block. A block the client never received has the unknown palette entry, which is how a block update
 * into a sub-chunk the server sent without block storage starts out.
 *
 * <p>Blocks are indexed as the network sub-chunk orders them, {@code (x << 8) | (z << 4) | y} of the coordinates
 * inside the sub-chunk. The network thread replaces and updates sections while players read them, so every access
 * holds the section's lock.</p>
 */
final class BlockSection {
	static final int BLOCKS = 4096;

	/**
	 * The palette entry of a block whose runtime id the client does not know; no int runtime id equals it.
	 */
	private static final long UNKNOWN = Long.MIN_VALUE;
	/**
	 * A palette this large, grown through block updates or sent so, is rebuilt from the blocks that use it before it
	 * grows further.
	 */
	private static final int COMPACT_THRESHOLD = 2 * BLOCKS;

	private long[] palette;
	private int paletteSize;
	private short[] indices;

	private BlockSection(long[] palette, int paletteSize, short[] indices) {
		this.palette = palette;
		this.paletteSize = paletteSize;
		this.indices = indices;
	}

	/**
	 * Creates a section whose blocks all have one runtime id.
	 *
	 * @param runtimeId runtime id of every block
	 * @return the section
	 */
	static @NotNull BlockSection uniform(int runtimeId) {
		return new BlockSection(new long[]{runtimeId}, 1, new short[BLOCKS]);
	}

	/**
	 * Creates a section whose blocks the client does not know yet.
	 *
	 * @return the section
	 */
	static @NotNull BlockSection unknown() {
		return new BlockSection(new long[]{UNKNOWN}, 1, new short[BLOCKS]);
	}

	/**
	 * Creates a section from a decoded paletted storage.
	 *
	 * @param palette runtime ids the storage's palette lists
	 * @param indices palette index of each block, in network order
	 * @return the section
	 */
	static @NotNull BlockSection paletted(int @NotNull [] palette, short @NotNull [] indices) {
		long[] entries = new long[palette.length];
		for (int i = 0; i < palette.length; i++)
			entries[i] = palette[i];

		return new BlockSection(entries, palette.length, indices);
	}

	/**
	 * Returns the index of a block inside its sub-chunk.
	 *
	 * @param x block x coordinate
	 * @param y block y coordinate
	 * @param z block z coordinate
	 * @return network index of the block
	 */
	static int index(int x, int y, int z) {
		return ((x & 15) << 8) | ((z & 15) << 4) | (y & 15);
	}

	/**
	 * Returns a block's runtime id.
	 *
	 * @param index network index of the block
	 * @return the runtime id, or empty when the client does not know it
	 */
	synchronized @NotNull OptionalInt get(int index) {
		long entry = palette[indices[index] & 0xFFFF];
		return entry == UNKNOWN ? OptionalInt.empty() : OptionalInt.of((int) entry);
	}

	/**
	 * Replaces a block.
	 *
	 * @param index network index of the block
	 * @param runtimeId runtime id the block now has
	 */
	synchronized void set(int index, int runtimeId) {
		// Finding the entry may compact the palette, which replaces the indices.
		int entry = paletteIndex(runtimeId);
		indices[index] = (short) entry;
	}

	private int paletteIndex(long entry) {
		for (int i = 0; i < paletteSize; i++)
			if (palette[i] == entry) return i;

		if (paletteSize >= COMPACT_THRESHOLD) compact();
		if (paletteSize == palette.length) palette = Arrays.copyOf(palette, paletteSize * 2);
		palette[paletteSize] = entry;

		return paletteSize++;
	}

	/**
	 * Drops the palette entries no block uses any more; at most one entry per block remains, below the threshold.
	 */
	private void compact() {
		int[] remapped = new int[paletteSize];
		Arrays.fill(remapped, -1);
		long[] compacted = new long[BLOCKS];
		int size = 0;
		short[] updated = new short[BLOCKS];
		for (int i = 0; i < BLOCKS; i++) {
			int old = indices[i] & 0xFFFF;
			if (remapped[old] < 0) {
				remapped[old] = size;
				compacted[size++] = palette[old];
			}
			updated[i] = (short) remapped[old];
		}

		palette = compacted;
		paletteSize = size;
		indices = updated;
	}
}
