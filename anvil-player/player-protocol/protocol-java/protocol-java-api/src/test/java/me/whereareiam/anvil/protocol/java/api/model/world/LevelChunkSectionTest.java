package me.whereareiam.anvil.protocol.java.api.model.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LevelChunkSectionTest {
	@Test
	void aUniformSectionHasOneStateEverywhere() {
		LevelChunkSection section = LevelChunkSection.uniform(9);

		assertEquals(9, section.blockState(0, 0, 0));
		assertEquals(9, section.blockState(15, 15, 15));
		assertEquals(LevelChunkSection.AIR, LevelChunkSection.EMPTY.blockState(7, 3, 2));
	}

	@Test
	void readsPalettedEntriesAlongXThenZThenY() {
		long[] data = new long[256];
		data[0] = 0x1L | 0x2L << 4;
		data[1] = 0x3L;
		data[64] = 0x1L << 60;
		LevelChunkSection section = LevelChunkSection.paletted(new int[] {0, 10, 20, 30}, 4, data);

		assertEquals(10, section.blockState(0, 0, 0));
		assertEquals(20, section.blockState(1, 0, 0));
		assertEquals(30, section.blockState(0, 0, 1));
		assertEquals(10, section.blockState(15, 4, 0));
		assertEquals(0, section.blockState(2, 0, 0));
	}

	@Test
	void keepsEntriesWithinOneLongWhenTheirBitsDoNotDivideItsSize() {
		long[] data = new long[(4096 + 11) / 12];
		data[0] = 17L << 55;
		data[1] = 9L;
		LevelChunkSection section = LevelChunkSection.direct(5, data);

		assertEquals(17, section.blockState(11, 0, 0));
		assertEquals(9, section.blockState(12, 0, 0));
	}

	@Test
	void readsDirectEntriesAsGlobalIds() {
		long[] data = new long[(4096 + 3) / 4];
		data[1023] = 27_000L << 45;
		LevelChunkSection section = LevelChunkSection.direct(15, data);

		assertEquals(27_000, section.blockState(15, 15, 15));
	}

	@Test
	void copiesEveryState() {
		long[] data = new long[256];
		data[255] = 0x1L << 60;
		int[] states = LevelChunkSection.paletted(new int[] {4, 5}, 4, data).blockStates();

		assertEquals(4096, states.length);
		assertEquals(5, states[4095]);
		assertEquals(4, states[0]);
		assertArrayEquals(new int[4096], LevelChunkSection.EMPTY.blockStates());
	}

	@Test
	void comparesByContent() {
		assertEquals(LevelChunkSection.paletted(new int[] {1, 2}, 4, new long[256]),
				LevelChunkSection.paletted(new int[] {1, 2}, 4, new long[256]));
		assertNotEquals(LevelChunkSection.paletted(new int[] {1, 2}, 4, new long[256]),
				LevelChunkSection.paletted(new int[] {1, 3}, 4, new long[256]));
	}

	@Test
	void refusesDataThatDoesNotHoldEveryEntry() {
		assertThrows(IllegalArgumentException.class, () -> LevelChunkSection.paletted(new int[] {1}, 4, new long[255]));
		assertThrows(IllegalArgumentException.class, () -> LevelChunkSection.direct(0, new long[0]));
		assertThrows(IllegalArgumentException.class, () -> LevelChunkSection.paletted(new int[0], 4, new long[256]));
	}

	@Test
	void refusesOffsetsOutsideTheSectionAndEntriesOutsideThePalette() {
		long[] data = new long[256];
		data[0] = 0x7L;
		LevelChunkSection section = LevelChunkSection.paletted(new int[] {1, 2}, 4, data);

		assertThrows(IndexOutOfBoundsException.class, () -> section.blockState(16, 0, 0));
		assertThrows(IllegalStateException.class, () -> section.blockState(0, 0, 0));
	}
}
