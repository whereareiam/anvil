package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByteArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtDouble;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtIntArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtLong;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtLongArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtShort;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbtTranslatorTest {
	private static final NbtMap LIBRARY = NbtMap.builder()
			.putByte("byte", (byte) 1)
			.putShort("short", (short) 2)
			.putInt("int", 3)
			.putLong("long", 4L)
			.putFloat("float", 0.5F)
			.putDouble("double", 0.25)
			.putString("string", "text")
			.putByteArray("bytes", new byte[]{1, 2})
			.putIntArray("ints", new int[]{3, 4})
			.putLongArray("longs", new long[]{5, 6})
			.putList("strings", NbtType.STRING, "a", "b")
			.putList("empty", NbtType.END, List.of())
			.putCompound("nested", NbtMap.builder().putString("id", "minecraft:diamond").build())
			.build();
	private static final NbtCompound PROTOCOL = NbtCompound.builder()
			.put("byte", new NbtByte((byte) 1))
			.put("short", new NbtShort((short) 2))
			.put("int", new NbtInt(3))
			.put("long", new NbtLong(4))
			.put("float", new NbtFloat(0.5F))
			.put("double", new NbtDouble(0.25))
			.put("string", new NbtString("text"))
			.put("bytes", new NbtByteArray(new byte[]{1, 2}))
			.put("ints", new NbtIntArray(new int[]{3, 4}))
			.put("longs", new NbtLongArray(new long[]{5, 6}))
			.put("strings", new NbtList(List.of(new NbtString("a"), new NbtString("b"))))
			.put("empty", NbtList.EMPTY)
			.put("nested", NbtCompound.EMPTY.with("id", new NbtString("minecraft:diamond")))
			.build();

	@Test
	void translatesEveryTagTypeTheLibraryReads() {
		assertEquals(PROTOCOL, NbtTranslator.compound(LIBRARY));
	}

	@Test
	void translatesEveryTagTypeBackForTheLibraryToWrite() {
		assertEquals(LIBRARY, NbtTranslator.map(PROTOCOL));
	}

	@Test
	void keepsTheOrderOfACompoundsEntries() {
		assertEquals(List.copyOf(LIBRARY.keySet()), List.copyOf(NbtTranslator.compound(LIBRARY).getEntries().keySet()));
	}

	@Test
	void unwrapsTheElementsOfAListOfMixedTypes() {
		NbtMap mixed = NbtMap.builder()
				.putList("with", NbtType.COMPOUND,
						NbtMap.builder().putString("", "Alice").build(),
						NbtMap.builder().putInt("", 5).build(),
						NbtMap.builder().putString("text", "!").build())
				.build();

		assertEquals(new NbtList(List.of(new NbtString("Alice"), new NbtInt(5), NbtCompound.EMPTY.with("text", new NbtString("!")))),
				NbtTranslator.compound(mixed).get("with"));
	}

	@Test
	void wrapsTheElementsOfAListOfMixedTypesAsTheGameWritesThem() {
		NbtCompound wrapperLike = NbtCompound.EMPTY.with("", new NbtString("kept"));
		NbtCompound mixed = NbtCompound.EMPTY.with("with", new NbtList(List.of(
				new NbtString("Alice"),
				new NbtInt(5),
				NbtCompound.EMPTY.with("text", new NbtString("!")),
				wrapperLike
		)));

		NbtMap written = NbtTranslator.map(mixed);

		assertEquals(NbtMap.builder()
				.putList("with", NbtType.COMPOUND,
						NbtMap.builder().putString("", "Alice").build(),
						NbtMap.builder().putInt("", 5).build(),
						NbtMap.builder().putString("text", "!").build(),
						NbtMap.builder().putCompound("", NbtMap.builder().putString("", "kept").build()).build())
				.build(), written);
		assertEquals(mixed, NbtTranslator.compound(written));
	}

	@Test
	void refusesAValueOfNoNbtType() {
		assertThrows(IllegalArgumentException.class, () -> NbtTranslator.tag(new Object()));
	}
}
