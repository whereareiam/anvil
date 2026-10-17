package me.whereareiam.anvil.protocol.java.api.model.nbt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtTagTest {
	@Test
	void writesABooleanAsAByteOfOneOrZero() {
		assertEquals(new NbtByte((byte) 1), NbtByte.of(true));
		assertEquals(new NbtByte((byte) 0), NbtByte.of(false));
	}

	@Test
	void readsEveryNumberInItsOwnTypeAndAsABoolean() {
		List<NbtNumber> numbers = List.of(new NbtByte((byte) 1), new NbtShort((short) 2), new NbtInt(3), new NbtLong(4),
				new NbtFloat(0.5F), new NbtDouble(0));

		assertEquals(List.of((byte) 1, (short) 2, 3, 4L, 0.5F, 0D), numbers.stream().map(NbtNumber::number).toList());
		assertEquals(List.of(true, true, true, true, true, false), numbers.stream().map(NbtNumber::asBoolean).toList());
	}

	@Test
	void keepsItsOwnCopyOfAnArray() {
		byte[] bytes = {1, 2};
		NbtByteArray array = new NbtByteArray(bytes);

		bytes[0] = 9;
		array.getValues()[1] = 9;

		assertArrayEquals(new byte[]{1, 2}, array.getValues());
		assertArrayEquals(new int[]{1, 2}, new NbtIntArray(new int[]{1, 2}).getValues());
		assertArrayEquals(new long[]{1, 2}, new NbtLongArray(new long[]{1, 2}).getValues());
	}

	@Test
	void comparesArraysByTheirValues() {
		assertEquals(new NbtIntArray(new int[]{1, 2}), new NbtIntArray(new int[]{1, 2}));
		assertNotEquals(new NbtIntArray(new int[]{1, 2}), new NbtIntArray(new int[]{2, 1}));
		assertEquals(new NbtLongArray(new long[]{3}).hashCode(), new NbtLongArray(new long[]{3}).hashCode());
	}

	@Test
	void keepsItsOwnUnmodifiableListOfElements() {
		List<NbtTag> elements = new ArrayList<>(List.of(new NbtString("easy")));
		NbtList list = new NbtList(elements);

		elements.add(new NbtString("hard"));

		assertEquals(List.of(new NbtString("easy")), list.getElements());
		assertThrows(UnsupportedOperationException.class, () -> list.getElements().add(new NbtInt(1)));
		assertTrue(NbtList.EMPTY.getElements().isEmpty());
	}

	@Test
	void picksTheElementsOfOneType() {
		NbtList mixed = new NbtList(List.of(new NbtString("easy"), NbtCompound.EMPTY, new NbtInt(1), NbtCompound.EMPTY));

		assertEquals(List.of(NbtCompound.EMPTY, NbtCompound.EMPTY), mixed.elements(NbtCompound.class));
		assertEquals(List.of(new NbtInt(1)), mixed.elements(NbtNumber.class));
		assertFalse(mixed.elements(NbtString.class).isEmpty());
	}
}
