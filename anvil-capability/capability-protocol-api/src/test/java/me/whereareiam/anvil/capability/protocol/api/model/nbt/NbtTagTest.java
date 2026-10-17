package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtTagTest {
	@Test
	void refusesAListOfMoreThanOneTagType() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> NbtList.of(NbtInt.of(1), NbtString.of("a")));

		assertTrue(failure.getMessage().contains("NbtInt and NbtString"), failure.getMessage());
		assertSame(NbtList.EMPTY, NbtList.of(List.of()));
		assertNull(NbtList.EMPTY.getElementType());
		assertEquals(NbtInt.class, NbtList.of(NbtInt.of(1)).getElementType());
	}

	@Test
	void keepsArraysImmutable() {
		byte[] values = {1, 2};
		NbtByteArray array = NbtByteArray.of(values);

		values[0] = 9;
		array.getValues()[1] = 9;

		assertArrayEquals(new byte[]{1, 2}, array.getValues());
		assertEquals(NbtByteArray.of((byte) 1, (byte) 2), array);
	}

	@Test
	void readsCompoundEntriesByTypeAndKeepsTheirOrder() {
		NbtCompound compound = NbtCompound.builder()
				.entry("name", NbtString.of("Alice"))
				.entry("age", NbtByte.of((byte) 7))
				.entry("home", NbtCompound.EMPTY)
				.entry("pets", NbtList.EMPTY)
				.entry("name", NbtString.of("Bob"))
				.build();

		assertEquals(List.of("name", "age", "home", "pets"), List.copyOf(compound.getEntries().keySet()));
		assertEquals("Bob", compound.getString("name"));
		assertEquals((byte) 7, compound.getNumber("age"));
		assertNull(compound.getString("age"));
		assertSame(NbtCompound.EMPTY, compound.getCompound("home"));
		assertSame(NbtList.EMPTY, compound.getList("pets"));
		assertTrue(NbtByte.of(true).asBoolean());
	}
}
