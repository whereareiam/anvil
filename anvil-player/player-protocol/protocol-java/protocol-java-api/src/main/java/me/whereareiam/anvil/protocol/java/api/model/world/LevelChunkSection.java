package me.whereareiam.anvil.protocol.java.api.model.world;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The block states of one 16 by 16 by 16 section of a chunk column, in the paletted form the server sends them: each
 * of the 4096 blocks is an entry of {@code bitsPerEntry} bits, packed into longs without crossing from one long into
 * the next, as every release since Minecraft 1.16 packs them. An entry is an index into the section's palette of
 * global block state IDs, or, for a direct section, the global ID itself. The entries run along x, then z, then y.
 *
 * <pre>{@code
 * LevelChunkSection section = LevelChunkSection.paletted(new int[] {0, 1}, 4, data);
 * int state = section.blockState(3, 0, 7);
 * }</pre>
 */
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
public final class LevelChunkSection {
	/**
	 * The global block state ID of air, the first block state of every release.
	 */
	public static final int AIR = 0;
	/**
	 * The blocks along each edge of a section.
	 */
	public static final int SIZE = 16;
	/**
	 * A section that holds only air, as the client keeps the sections a chunk packet leaves out.
	 */
	public static final @NotNull LevelChunkSection EMPTY = uniform(AIR);

	private static final int BLOCKS = SIZE * SIZE * SIZE;
	private static final int BITS_PER_LONG = Long.SIZE;
	private static final int MAX_BITS_PER_ENTRY = Integer.SIZE - 1;

	/**
	 * The global IDs the entries index, or {@code null} when the entries are global IDs themselves.
	 */
	private final int[] palette;
	/**
	 * The bits of each entry; zero for a section of one block state, which has no entries.
	 */
	@ToString.Include
	private final int bitsPerEntry;
	private final long[] data;

	private LevelChunkSection(int[] palette, int bitsPerEntry, long[] data) {
		this.palette = palette;
		this.bitsPerEntry = bitsPerEntry;
		this.data = data;
	}

	/**
	 * Creates a section whose blocks all have one state, which the server sends without entries.
	 *
	 * @param blockState the global ID of every block's state
	 * @return the section
	 */
	public static @NotNull LevelChunkSection uniform(int blockState) {
		return new LevelChunkSection(new int[] {blockState}, 0, new long[0]);
	}

	/**
	 * Creates a section whose entries index a palette of global IDs.
	 *
	 * @param palette the global IDs in palette order; the section keeps a copy
	 * @param bitsPerEntry the bits of each entry
	 * @param data the packed entries; the section keeps a copy
	 * @return the section
	 * @throws IllegalArgumentException when the palette is empty or the data does not hold 4096 entries of that size
	 */
	public static @NotNull LevelChunkSection paletted(int @NotNull [] palette, int bitsPerEntry, long @NotNull [] data) {
		if (palette.length == 0) throw new IllegalArgumentException("A paletted section needs at least one palette entry");

		return new LevelChunkSection(palette.clone(), checkedBits(bitsPerEntry), checkedData(bitsPerEntry, data));
	}

	/**
	 * Creates a section whose entries are global IDs, as the server sends a section of too many states for a palette.
	 *
	 * @param bitsPerEntry the bits of each entry
	 * @param data the packed entries; the section keeps a copy
	 * @return the section
	 * @throws IllegalArgumentException when the data does not hold 4096 entries of that size
	 */
	public static @NotNull LevelChunkSection direct(int bitsPerEntry, long @NotNull [] data) {
		return new LevelChunkSection(null, checkedBits(bitsPerEntry), checkedData(bitsPerEntry, data));
	}

	/**
	 * Returns the state of one block of the section.
	 *
	 * @param x the block's east-west offset in the section, from 0 to 15
	 * @param y the block's height in the section, from 0 to 15
	 * @param z the block's north-south offset in the section, from 0 to 15
	 * @return the global ID of the block's state
	 * @throws IndexOutOfBoundsException when an offset lies outside the section
	 * @throws IllegalStateException when the block's entry names no entry of the palette
	 */
	public int blockState(int x, int y, int z) {
		int index = (Objects.checkIndex(y, SIZE) << 8) | (Objects.checkIndex(z, SIZE) << 4) | Objects.checkIndex(x, SIZE);
		if (bitsPerEntry == 0) return palette[0];

		int entriesPerLong = BITS_PER_LONG / bitsPerEntry;
		long cell = data[index / entriesPerLong];
		int entry = (int) ((cell >>> (index % entriesPerLong * bitsPerEntry)) & ((1L << bitsPerEntry) - 1));
		if (palette == null) return entry;
		if (entry >= palette.length)
			throw new IllegalStateException("Entry " + entry + " of the block at " + x + " " + y + " " + z
					+ " names no entry of a palette of " + palette.length);

		return palette[entry];
	}

	/**
	 * Copies every block's state, in the order the entries run: along x, then z, then y.
	 *
	 * @return the 4096 global IDs
	 */
	public int @NotNull [] blockStates() {
		int[] states = new int[BLOCKS];
		for (int y = 0; y < SIZE; y++)
			for (int z = 0; z < SIZE; z++)
				for (int x = 0; x < SIZE; x++) states[(y << 8) | (z << 4) | x] = blockState(x, y, z);

		return states;
	}

	private static int checkedBits(int bitsPerEntry) {
		if (bitsPerEntry < 1 || bitsPerEntry > MAX_BITS_PER_ENTRY)
			throw new IllegalArgumentException("A section's entries take from 1 to " + MAX_BITS_PER_ENTRY + " bits, not "
					+ bitsPerEntry);

		return bitsPerEntry;
	}

	private static long[] checkedData(int bitsPerEntry, long[] data) {
		int entriesPerLong = BITS_PER_LONG / bitsPerEntry;
		int longs = (BLOCKS + entriesPerLong - 1) / entriesPerLong;
		if (data.length != longs)
			throw new IllegalArgumentException("4096 entries of " + bitsPerEntry + " bits take " + longs + " longs, not "
					+ data.length);

		return data.clone();
	}
}
